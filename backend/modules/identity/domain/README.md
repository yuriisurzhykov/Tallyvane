# identity:domain

Entities and value objects for authentication, invisible outside `identity:application` and
`identity:infrastructure` (`domain-no-application`, `modules.yaml`: `domain` depends on nothing but
`platform:kernel`).

```
user/         User, UserId, Email
session/      Session, SessionId, DeviceLabel
token/        TokenKind, TokenValue, TokenPair, HashedToken, TokenFamilyId, TokenFamilyState,
              RefreshRotationDecision, RefreshRotationPolicy
credential/   Credential (sealed: PasswordRecord, GoogleRecord), PasswordHash, GoogleSubject
secondfactor/ SecondFactorKind, PendingAuthenticationId, PendingAuthentication, EncryptedSecret
secondfactor/totp/ TotpEnrollment
outcome/      AuthenticationOutcome, RegisterOutcome, SecondFactorOutcome
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

## `PendingAuthentication` carries `device`, which the design's own field list did not name

The design plan's field list for this entity was "who, which factors are still available, when it
expires" — three concepts, not four. Building it that way left no way for
[`VerifySecondFactorUseCase`](../application/README.md) to hand [`SessionIssuer`](../application/README.md)
a device label once a second factor completes, since a `Session` needs one and re-asking the client
for it on the `/auth/mfa/verify` request risks it silently disagreeing with the one the primary
sign-in already presented seconds earlier. `PendingAuthentication.device` is a correction against
the plan's sketch, the same kind of gap `SessionIssuer`'s own README entry already records for
`issue()`'s return type — recorded here rather than silently added without comment
(`backend/.plans/identity-implementation.md`).

`availableMethods` validates non-empty in `init`: nothing constructs this case unless at least one
[`SecondFactorKind`](secondfactor/SecondFactorKind.kt) is actually enrolled, so a
`PendingAuthentication` that nothing could ever complete is a state this type refuses to hold.

## `EncryptedSecret` is a base64 `String`, not a raw `ByteArray`

TOTP's seed is the first secret in this module that must be read back — a password or token hash
never is, so [`PasswordHash`](credential/PasswordHash.kt) and
[`HashedToken`](token/HashedToken.kt) could stay one-directional. A `value class` over a raw
`ByteArray` compares and hashes by reference, not content — a real Kotlin gotcha, not a
hypothetical one — so [`EncryptedSecret`](secondfactor/EncryptedSecret.kt) wraps the
already-base64-encoded text
[`SecretCipher`](../application/src/main/kotlin/tallyvane/identity/application/port/SecretCipher.kt)
hands back, the same choice `HashedToken` already made for a hash function's raw output.

## `TotpEnrollment.active` starts `false`, and the field exists precisely so it can

[`TotpEnrollment`](secondfactor/totp/TotpEnrollment.kt) is created the moment enrollment starts,
before anyone has proven they can actually produce a code from it — scanning the wrong QR code, or
a screenshot saved and never scanned at all, are both real failure modes a TOTP setup flow has to
survive. `active` is what keeps
[`SecondFactorMethod.isEnrolledFor`](../application/src/main/kotlin/tallyvane/identity/application/port/SecondFactorMethod.kt)
answering `false` until `confirmEnrollment` proves the account holder captured the seed correctly —
`application/README.md` has the full enrollment/confirm split.

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
