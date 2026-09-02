# identity:domain

Entities and value objects for authentication, invisible outside `identity:application` and
`identity:infrastructure` (`domain-no-application`, `modules.yaml`: `domain` depends on nothing but
`platform:kernel`).

```
user/        User, UserId, Email
session/     Session, SessionId, DeviceLabel
token/       TokenKind, TokenValue, TokenPair, HashedToken, TokenFamilyId, TokenFamilyState,
             RefreshRotationDecision, RefreshRotationPolicy
credential/  Credential (sealed: PasswordRecord), PasswordHash
outcome/     AuthenticationOutcome, RegisterOutcome
```

## What needed deciding, and what was actually built

The four slices that built this layer (`backend/.plans/identity-implementation.md`) wrote every type
into one flat package and stayed there — seventeen files with no grouping, until the author named it
for what it was. The fix follows a precedent already in this tree rather than inventing a new one:
`platform:http` already splits its own single layer into `problems/` and `status/`, a package per
concept a reader would name unprompted, not a flat list of every file the layer happens to contain.
`package-matches-layer` (Konsist) allowed any subpackage of `tallyvane.identity.domain.*` from the
day this module started; the flat layout was never a rule's requirement, only an unexamined default.
Full account of the mistake and the fix, dated: `backend/.plans/identity-implementation.md`'s "Разбор
автора" entry.

`outcome/` sits apart from `user`/`session`/`token`/`credential` on purpose. `AuthenticationOutcome`
and `RegisterOutcome` are not owned by any one entity more than another — each is what a use case in
`application` hands back to its caller, not a fact about a user or a session considered alone.
Grouping either with `user` because it carries a `UserId` would tie a cross-cutting result type to
one entity for no reason but a shared field.

## Why this module's `UserId`/`SessionId` duplicate `identity:contract`'s

See `contract/README.md`'s own entry on this — the two are independent by the same layer boundary in
both directions, not an oversight either module should "fix" by importing the other.

## Three deliberate omissions, and why each one is not a gap

- **`Email` compares case-sensitively.** This project's own persistence skill settled
  case-insensitive lookup as a Postgres column collation (`platform.case_insensitive`), not as
  normalisation inside the value object. Folding case here too would give this type and the
  database two different, silently disagreeing notions of "the same address".
- **`Session` carries no token or hash field.** Matches the design's own description of the
  `identity.sessions` row — "principal reference, a human-readable label, last_used_at, current
  token family id" — nothing about a token value. Where a token's hash is looked up from, for
  validating a presented access token or detecting a reused refresh token, is a storage design the
  persistence slice has not made yet; `SessionIssuer`'s own README entry names the corrected first
  draft this produced.
- **`HashedToken.hash` is a `Secret`, never a bare `String`.** Comparing two of these must run in
  constant time regardless of how many leading characters match —
  `backend/.plans/backend-access-and-api.md` §7.3 names this as a requirement for every token in
  this system, not only the service health token ADR-063 already gave it to.

## Two places the design's own sketch did not survive contact with this codebase

- **`RefreshRotationPolicy`.** The design sketched `object RefreshRotationPolicy`.
  `no-stateful-objects` forbids exactly this shape — any non-companion `object` declaring a function
  — so it is an interface with a nested `Default`, the same shape `platform:kernel`'s `Clock.Wall`
  already takes for a stateless singleton with behaviour.
- **`RefreshRotationDecision.Rotate`.** The design attached a freshly minted `TokenPair` to this
  case. `decide()` is a pure function with no I/O — it cannot reach `TokenFactory`, an
  `application`-layer port this layer is not allowed to see (`modules.yaml`) — so `Rotate` carries no
  payload. Minting the new pair is the calling use case's job, after the decision, not the policy's.

## Understandable, scalable, extensible

A reader looking for "what is a session" finds `session/Session.kt` and nothing else competing for
the same name in a dozen other files. Adding a second credential kind (`GoogleRecord`, named in the
design but not built) is a new case on `Credential`, in the same package, not a new top-level type
requiring every existing caller of `Credential` to be re-read.

## Migration and fault tolerance

No schema yet. `identity.users`, `identity.sessions`, `identity.credentials` and the rest arrive with
the persistence slice, each type here becoming the shape an Exposed table maps to, not before.

## The SOLID angle

**Single responsibility.** Each package answers one question about one concept; `Email`'s only job
is "is this string shaped like an email", not also "does this email already belong to a user" —
that question is `UserRepository.findByEmail`'s, in `application`.

**Open/closed.** `Credential` is sealed specifically so a second credential kind is a new case, not a
rewrite of `PasswordRecord`'s existing callers.

**Liskov substitutability.** `RefreshRotationPolicy.Default` is the only implementation today, but
the interface exists so a future policy — one that also weighs a device fingerprint, say — can
replace it without any caller's code changing.

**Interface segregation.** `RefreshRotationPolicy` has one method, deciding reuse. Expiry is a
separate, simpler question this layer deliberately does not fold in — `TokenFamilyState`'s own KDoc
names why the two must not share an answer.

**Dependency inversion.** Nothing here imports a port or a driver. Every type in this layer is what
`application` depends on, never the reverse.
