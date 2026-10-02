# ADR-086. A request is claimed by its Idempotency-Key in the transaction it runs in

## Status

Accepted. Chosen by the owner on 2026-10-01, question by question (`/mnt/project-files/idempotency/`):
the claim shares the transaction of the work (Q1 B), its owner is the authenticated subject (Q2 A), a
second copy waits for the first (Q3 A), an answer that carries a credential is not stored (Q4 A), the
key is required on every unsafe method (Q5 B) and kept for 24 hours.

## Context

ARCHITECTURE.md §11.1 says unsafe methods "accept" an `Idempotency-Key` and §9.4 says a client never
sees an error caused by sending something twice. Nothing in the code did either, and the case that
matters is not the double click. It is a request that *succeeded*, whose answer was lost on the way back:
the client cannot tell that from a request that never arrived, so it sends the same thing again.

Three situations have to end in one effect:

1. The first request never reached the server, or its transaction rolled back. Nothing was written, and
   running the second is correct. ADR-052 already guarantees this half: one use case is one transaction.
2. The first request committed and its answer was lost. Running the second does the work twice.
3. Two copies are in flight together. Both see "not done yet".

Sign-in makes the stakes concrete. A wrong code recorded twice is two of the five failures an attempt is
allowed (ADR-078), so a flaky connection would lock someone out of their own account.

The server cannot recognise a repeat by content: two identical `POST /notes` are as likely to be two
notes as one. So the client says so, with a key it generates once and sends again unchanged.

## Decision

**Every `POST`, `PUT`, `PATCH` and `DELETE` carries `Idempotency-Key: <uuid>`.** A request without one
is refused with 400, a key that is not a UUID with 400. A client has no reason to omit it (generating a
UUID costs nothing) and a missing one is the case that duplicates a payment. The extension's
deduplication key of §9.4 (address, action, local date) is made into a UUID by hashing it, so it keeps
its meaning.

**A claim is the pair (owner, key).** The owner is the subject the request's authentication
established: the user, whether they came by cookie or by device token, or an administrator. A request
with no authentication (the start of a Google sign-in, for instance) has the owner *anonymous*, which is
all of them today because sessions do not exist yet; the session slice changes one function. Keys are
therefore private to their owner: a key seen in a log or a screenshot returns nothing to anyone else.

**A claim records a fingerprint of the request**, SHA-256 over the method, the request target (path and
query) and the body. The same pair with a different fingerprint is a client that reused a key, and is
refused with 422 without running anything.

**The claim is taken in the work's own transaction.** The HTTP layer puts the claim in the coroutine
context, as it does the trace. The `TransactionRunner` every use case receives is a decorator that, when a
claim is present, inserts it as the *first statement* of the transaction and only then runs the use
case's block. The claim commits with the work or rolls back with it, in one atomic step, by the
database's own guarantee. A use case contains no line about idempotency and cannot forget one.

The insert is `INSERT ... ON CONFLICT (owner, key) DO UPDATE ... WHERE expires_at <= now`: a live claim
refuses it, an expired one is taken over. When it refuses, the transaction ends with `ClaimedElsewhere`,
which unwinds through the use case, rolling everything back, to the HTTP layer.

**What the HTTP layer does, in this order:**

| What it finds | What the client gets |
|---|---|
| no earlier claim | the request runs |
| claim, same fingerprint, answer stored | the stored answer again, with `Idempotent-Replayed: true` |
| claim, different fingerprint | `422`, the key was reused for another request |
| claim, committed, answer deliberately not stored | `409`, the work was done and cannot be repeated; reload |
| claim, committed, answer not stored yet | waits up to about 750 ms for the answer, then the `409` above |
| a copy is mid-transaction | the second waits at the insert, then gets one of the rows above, or runs if the first rolled back |
| the wait ran out (`lock_timeout`, 3 s) | `409` with `Retry-After`, try the same request again |

**The answer is stored after the commit**, in a short transaction of its own, before the response is
sent: the status, the content type and the body, never the headers. A response is stored only if its
status is below 500 and its body is bytes the layer can read. A 5xx is not stored: if the work rolled
back there is no claim and the retry should run, and if it committed and the response then failed, the
client should be told the truth (409) and not handed a 500 for something that succeeded.

**Answers that carry a credential are not stored.** A response with `Set-Cookie` is marked withheld, and
so is one that a route marks with `SecretAnswer(call).withheldFromReplay()`. The second is beyond what Q4
A said and is here because the device-token exchange of ADR-081 returns its secret in the body, and the database
holds only hashes of secrets (ADR-079). A repeat of a withheld answer is the same 409 as a lost one.

**One request, one claimed transaction.** A transaction that ended without committing (the block threw,
or it said `Rollback`) leaves the claim free, so a retry written by the use case takes it again. A
second transaction opened after a claimed one *committed* is refused with `IllegalStateException`: its
writes would sit outside the claim, so after a crash between the two a repeat would see "done" for work
that was only half done. This is ADR-052's rule against nesting, applied in sequence.

**Claims live 24 hours.** An expired claim is taken over by the next request with its key, and a sweep
(`ClaimsSweep`, started by the composition root in a scope it cancels on the way out) deletes the expired
ones hourly, the first time one hour after the process starts. A failed sweep is logged and the next one
tries again, since nothing waits on it. A client repeating a request after a day is not repeating it.

**A body larger than 1 MiB is refused with 400** for the same reason a fingerprint exists: the layer
reads the body to hash it. No route takes more today. A route that must (document upload) needs its own
decision, most likely a hash computed while streaming.

### Where it lives

A new module, `platform:idempotency`, holds the vocabulary, the two ports and the decorator: `Ledger`,
which the HTTP layer asks (what is known, store the answer, forget what expired), `Claims`, which only
the decorator calls (take one, inside the open transaction), and `ClaimedTransactions`, the decorator.
`platform:persistence` implements both ports over Postgres, in a table `platform.idempotency_keys`, and
`PostgresPersistence.transactions` is the decorated runner, so there is no way to obtain an undecorated
one by accident. `platform:http` owns the header, the fingerprint and the six refusals. A new module
rather than more kernel: it is a dozen types with two adapters, which is not the ten lines `kernel`
admits to.

Neither port knows HTTP and neither knows Postgres. The module ships a fake of both and one conformance
suite that runs against the fake and against Postgres (ADR-046), so the fake cannot drift from the table.

## Alternatives considered

**Q1 A. Claim at the entrance, in its own transactions** (claim, run, store the answer). The use case
knows nothing, as here. But the claim and the work commit separately, so a process that dies between
them leaves a claim saying "in progress" for work that is either done or not, and the repeat cannot tell
which. Stripe closes that gap with recovery points written by hand into every handler. Rejected: it moves
a hard problem into every use case.

**Q1 C. A key stored in each aggregate** (a column on the note, the attempt, the payment), as §9.4 does
for events. Exactly right and no window at all, and the cost is that every module re-solves it and every
new endpoint has to remember to. Kept as the answer for the places that need *no* window: the event
log's own dedup key is unchanged.

**Q2 B. Owner is the session or token.** Stricter, and wrong where it matters: a token refreshed between
the first request and its repeat makes the repeat a stranger. **Q2 C. No owner.** A leaked key would
replay someone else's answer.

**Q3 B. A second copy gets 409 at once.** Simple, and puts an error in front of the client for what would
have been an identical answer 20 ms later, against §9.4.

**Q4 B. The route says whether its answer may be stored.** Flexible, and a route that forgets stores a
secret. The decision went the other way round: stored unless it carries a cookie, with an opt-out for the
cases where the secret is in the body.

**Q5 A. The key required only where a route says so.** The same forgetting, one level up.

**Seven days instead of one.** Room for an offline mobile client, at seven times the table. The number is
one constant.

**Redis or memory.** A second store to lose, with no way to join the database's transaction. A claim in
memory is gone after a restart and not shared by a second instance, which is when a repeat is most
likely.

## Consequences

Every client (the app, the extension, the mobile app, our tests) sends a key on every unsafe call. The
OpenAPI document of slice 14 declares it as a required header.

The stored answer has no headers, so a route's reply has to carry what the client needs in its body: a
`Location` header would be missing on a repeat. §11.1's conventions are JSON bodies already.

Two `409` answers share a type, since the closed set of meanings (ADR-062) has one conflict. They differ
by `Retry-After`: present means "try the same thing again", absent means "do not, reload".

A copy that waits holds a pooled connection for as long as it waits, at most 3 seconds (ADR-058 sizes the
pool at 8), so a burst of identical requests costs connections, not correctness.

The claim protects what is written to Postgres in a transaction, which is everything this system writes
(ADR-049). It does not protect a call to a third party made inside the request. That is the outbox's
half (§6.23), and still empty.

A request that never opens a transaction (every refusal decided before one, a read) leaves no claim, so
its repeat simply runs again, which is correct since it wrote nothing.

## Not decided here

Where the owner comes from once there are sessions: settled by ADR-088. The edge asks a `Callers` port once per
request and the person is the owner; `Owners` no longer exists.
