# platform:idempotency

What the system remembers about requests it has already seen, so that sending one twice is the same as
sending it once. The decision and its alternatives are in
[ADR-086](../../../docs/adr/ADR-086-a-request-is-claimed-in-the-transaction-it-runs-in.md); this file is
about the module that carries it.

## What a use case sees

Nothing. A use case takes a `TransactionRunner` and writes `inTransaction { ... }`, exactly as before.
The runner it receives is `ClaimedTransactions`: when the request that is running carries a `Claim` in its
coroutine context, the first statement of the transaction takes it, and only then does the use case's block
run. The claim commits with the work or rolls back with it, by the database's own guarantee, so there is no
state in which the work was done and the claim was not, or the other way round.

```
HTTP edge          ledger.earlier(claim)        what do we know? (committed claims only)
   │               withContext(claim) { route }
   │                  └─ use case ─ inTransaction ─ claims.take(claim)   ← first statement
   │                                                 ...the use case's own writes...
   │                                                 COMMIT, claim and work together
   └───────────────ledger.record(claim, answer)   after the commit, a transaction of its own
```

## The pieces

| Type | Is |
|---|---|
| `IdempotencyKey`, `Owner`, `Fingerprint` | The three facts a claim is made of. `IdempotencyKey.parse` answers `null` for what is not a UUID. |
| `Claim` | A coroutine context element. Tells its facts to a `Claim.Record`; has no getters. |
| `Ledger` | What the edge asks: `earlier`, `record`, `withhold`, `forgetExpired`. Every call is on a connection of its own. |
| `Claims` | What only the decorator calls: `take`, inside the open transaction. |
| `Earlier` | The ledger's answer: `None`, `Different`, `Replay`, `Withheld`, `Unanswered`. |
| `ClaimedTransactions` | The decorator that makes the claim the first statement. |
| `ClaimedElsewhere`, `ClaimBusy` | What `take` throws: the key is live and committed elsewhere, or the lock wait ran out. They unwind through the use case on purpose. |
| `ClaimsSweep` | Deletes expired claims on a timer, in a scope its owner cancels. |

`Ledger` and `Claims` are two ports and not one because they need different connections. `earlier` must see
only committed claims and `record` must happen after the commit, so neither can share the work's
transaction; `take` must share it, so it cannot have a connection of its own.

## One request, one claimed transaction

A second transaction opened after a claimed one *committed* throws `IllegalStateException`. Its writes
would sit outside the claim, and after a crash between the two a repeat would see "done" for work that was
half done. A transaction that rolled back or threw leaves the claim free, so a retry written by the use case
takes it again. This is ADR-052's rule against nesting transactions, applied in sequence.

## Tests, and what each one was watched failing against

`LedgerConformance` is one suite that runs against `LedgerFake` and, in `platform:persistence`, against
Postgres, so the fake cannot drift from the table (ADR-046). It does not cover two requests in flight at
once; the Postgres spec in `platform:persistence` holds the first request open on purpose, because a race a
test merely hopes for passes on the day nothing raced.

Each case was run against a deliberately broken implementation first:

- never taking the claim: the conformance suite and the concurrency spec go red, and so does the server's
  six-copies-at-once case;
- taking over a live claim, or never taking over an expired one: the concurrency spec and the expiry case;
- allowing a second committed transaction: `ClaimedTransactionsSpec`;
- a sweep that runs once at start instead of waiting: `ClaimsSweepSpec`; a process that never starts one:
  the case in `ApplicationIntegrationSpec`.

One test was found to be empty by doing this. The HTTP spec's case for "a copy that loses the race at the
claim" used a ledger that did not see the *first* claim asked about, which is the first request's, not the
copy's, so the race it described never happened and the case passed with the code that handles it
removed. It blinds the second asker now, and fails without that code.

## Not here

The header, the fingerprint of a request and the HTTP answers are `platform:http`'s (`Repeats`,
`KeptAnswers`). The table and the SQL are `platform:persistence`'s. Calls to a third party made inside a
request are the outbox's half and are not protected by this.
