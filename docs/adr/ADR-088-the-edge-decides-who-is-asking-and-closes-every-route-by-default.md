# ADR-088. The edge decides who is asking and closes every route by default; `sessions` issues what it recognises

## Status

Accepted. Chosen by the owner on 2026-10-02, fork by fork, while planning the sessions slice
(`/mnt/project-files/auth-design/slice-3/03-plan-3b.md`): `sessions` names the account by `identity`'s typed
`AccountId`, not by a bare UUID and not through the kernel (fork 1); the edge asks a `Callers` port who is
asking (2); routes are closed unless they say otherwise (3); `GET /me` lives in `identity/web` (4); one pull
request (5).

## Context

ADR-079 chose server-side opaque sessions and ADR-080 the cookie that carries them, the check of where an
unsafe request comes from, and JSON-only bodies. ADR-084 chose how an ended session is reported. ADR-086 left
one thing open: where the owner of an `Idempotency-Key` comes from once there are sessions, and until then
every request was anonymous (`Owners.Anonymous()`).

Three questions were left. How does a request learn who it is from, before the idempotency claim is made and
without `platform:http` knowing what a session is. What happens to a route nobody thought about. And what does
`sessions` need from `authentication`, which must never name a user, and from `identity`, which must never
know about sessions.

## Decision

**The edge asks a `Callers` port, once per request.** `Callers.of(call)` answers a `Caller`: `Signed` (a
person), `Lapsed` (a credential was presented and is no good) or `Anonymous` (none was). `Api` calls it before
`Repeats` takes its claim and keeps the answer on the call; the claim's owner is the person, and nobody
otherwise. `sessions` supplies the implementation, which reads the `__Host-session` cookie. `platform:http` knows
no cookie, no token and no session, so a bearer token (ADR-081) is a second `Callers`, not a second mechanism.
The `Owners` port is gone: it answered a question the caller now answers once.

**Every route is closed unless it says it is public.** `RouteModule.access` defaults to `Access.Signed`; a route a
stranger may reach says `Access.Public`. The gate runs ahead of the idempotency claim, so a refused request
leaves no claim. It decides from the path, and an address is open only when it plainly names a public prefix:
no empty, `.` or `..` segment, no `%`, no backslash. Anything it cannot read with certainty is closed, which
costs a client a `401` and costs a bypass nothing to find. An address nobody mounted under `/api/v1/` is closed
too, so a stranger cannot tell a missing route from a closed one. Outside the API nothing is decided here.

**Refusals are two problems, not one.** No credential is `401 sign-in-required`; a credential that no longer
works is `401 session-expired` (ADR-084). The client reacts differently: it sends the first to the sign-in page
and shows the second over the page it is on. Both are in the closed set of ADR-062 as new meanings
(`signInRequired`, `sessionExpired`), which makes nine.

**An unsafe request must come from the application's own origin, and be JSON.** `POST`, `PUT`, `PATCH` and
`DELETE` need an `Origin` header equal to `TALLYVANE_APP_ORIGIN`, compared exactly (a sibling subdomain is
another origin; so is one that merely begins with ours), or, when the browser sent none, `Sec-Fetch-Site:
same-origin`. Neither is `403`. A body that is not JSON is `415`; a request with no body is let through. The
exemption for requests that authenticate by `Authorization: Bearer` is not made here: it arrives with the
tokens.

**`authentication` offers a contract, and `sessions` redeems through it.** `SignIns.redeem(secret)` runs in the
caller's transaction and returns `Redeemed(account, proofs, authenticatedAt)` or `NothingToRedeem`, taking the
completed attempt with it: a sign-in is single-use. Only a sign-in the policy of its purpose calls complete,
and whose person has an account, is handed over, so a registration whose welcome form is unfinished stays where
it is for the form. The account is `identity`'s `AccountId`, which a contract layer may read from another
module's contract (the one change to `modules.yaml`'s layer rules); `sessions` therefore reads
`[authentication, identity]` and neither of those reads it.

**A session is a record, and a replacement, never an edit.** It holds the account, how the person proved
themselves (`Factor`, which `sessions` keeps as its own list so `authentication` can change its model without
this module noticing), when they did, and when the session was last used. The browser holds 256 random bits;
the database holds `HMAC-SHA256(pepper, secret)` and the pepper's version, the same construction as ADR-087
and the same pepper. The secret is always new, never the attempt's own, so whoever planted a known cookie
before the sign-in does not hold the session afterwards. Lifetimes are applied to the session as it is on every
request, not frozen at issue: 1 day idle, 7 days absolute, constants until the settings slice makes them a
policy. A session found to be over is forgotten on the spot, which is all the cleaning there is for now. Last
use is kept to one minute, in the `WHERE` of a single `UPDATE`, so a person who clicks around does not write on
every request and a request that does not qualify takes no row lock.

**Sign-out is public and always `204`.** A person whose session has lapsed can still clear their cookie, and
whoever asks to be signed out is.

## Alternatives considered

**`authenticate { }` of Ktor in each module.** It runs after the idempotency claim, so the claim could not be
owned by the person, and a route that forgot to declare it would be open. **A bare `Uuid` for the account.** Any
UUID would pass for an account, and the two modules would agree on a convention instead of a type. **`AccountId`
in `platform:kernel`.** The kernel is what every module may read, and an account is not that. **Open by default
with a marker for closed routes.** One forgotten line publishes an endpoint; closed by default turns the same
mistake into a `401` someone notices on the first request. **Matching the path in the gate as the router will.** The
two can disagree about an encoded or dotted path, and a disagreement is a bypass; refusing what is not plain
needs no agreement. **Signing the cookie instead of keeping a session.** Nothing to revoke, which is the
reason ADR-079 refused it.

## Consequences

The session cookie is `Secure` and `__Host-`, so a development browser needs HTTPS or `localhost`, which Chrome
and Firefox treat as secure.

`POST /api/v1/sessions` sets a cookie, so by ADR-086 a repeat of its `Idempotency-Key` is not replayed and
is answered `409` without `Retry-After`. That is the right answer: the sign-in was single-use and the work was
done. `DELETE /api/v1/session` needs an `Idempotency-Key` like any unsafe request.

`GET /api/v1/me` answers `401 session-expired` for a session that names an account that is gone, so a deleted
account cannot go on using what it was given.

The `migrate` job now carries the `identity` and `sessions` migrations on its classpath. Until this slice only
`authentication`'s were there, so a deploy that ran `migrate` alone would not have created the `identity`
schema.

Open: sessions of a deleted account are not removed (there is no deletion yet); the list of a person's
sessions, with device descriptions, arrives with the settings slice; a bearer token exemption from the origin
check arrives with ADR-081's tokens.
