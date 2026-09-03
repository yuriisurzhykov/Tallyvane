# identity:application

Ports and use cases — the layer that turns `domain`'s types into something a caller can actually
invoke: register, sign in, issue a session.

```
port/           TokenFactory, TokenHasher, SessionStore, PasswordHasher, UserRepository,
                CredentialRepository, LoginAttempts, GoogleOAuthGateway, GoogleIdTokenVerifier,
                PendingAuthenticationStore, SecondFactorMethod (+ nested Rfc6238), SecretCipher,
                TotpEnrollmentStore
password/       RegisterWithPasswordUseCase, SignInWithPasswordUseCase (+ their requests)
googleoauth/    SignInWithGoogleOAuthUseCase (+ its request)
googlecredential/ SignInWithGoogleCredentialUseCase (+ its request)
google/         GoogleIdentity, GoogleSignInCompleter — shared by both Google methods, filed under
                                                          neither because it belongs to neither alone
secondfactor/   SecondFactorMethodRegistry, VerifySecondFactorUseCase (+ its request/outcome),
                EnrollSecondFactorUseCase, ConfirmSecondFactorEnrollmentUseCase
secondfactor/totp/ Base32, Rfc6238Totp — the pure encoding and math `SecondFactorMethod.Rfc6238`
                                          composes, each independently tested against a published RFC
SessionIssuer.kt, IssuedSession.kt, AuthenticationCompleter.kt  — shared by every method, not filed
                                                          under any one package
```

## Why there is no `AuthenticationMethod<TCredential>` port, despite the design plan naming one

The design plan's own component diagram shows every primary sign-in method behind a shared
`AuthenticationMethod<TCredential>` Strategy. Building it before a second real implementation
existed to compare against would have meant extracting a shared interface on the strength of a
prediction — `ENGINEERING-PRINCIPLES.md`'s "A Strategy is extracted from a second real
implementation, not predicted from one" names exactly this mistake, made once already in this
module's own history and reverted (`backend/.plans/identity-implementation.md`). Now that
`SignInWithGoogleOAuthUseCase.SignIn` exists too, the comparison has actually been made — see the
next section — and it found nothing worth sharing.

## What `SignIn` (password) and `SignIn` (Google OAuth) do and do not have in common

The only shared code between the two is three lines at the very end: build a `Principal.User`,
call `sessions.issue(...)`, wrap the result as `SignInOutcome`. The credential check itself shares
nothing — password is a synchronous hash comparison over `UserRepository`/`CredentialRepository`/
`PasswordHasher`; Google is a network round trip to a third party, a JWT signature check against a
published JWKS, and, on a first sign-in, a new account created inside a transaction. Three shared
lines do not earn an interface; the duplication stays, recorded here rather than resolved silently
in a diff (`backend/.plans/identity-implementation.md`, срез 6).

**2026-09-02 — the *second* Google method changed this answer, for Google specifically.** Google
Identity Services (`googlecredential/`) needed exactly the sequence Google OAuth
(`googleoauth/`) already had: given a verified `GoogleIdentity`, find the account by `subject`,
create one if none exists, refuse on an email collision, then issue a session. That is not three
incidental lines — it is the entire "what happens once we trust this identity" logic, byte-for-byte
identical, because both methods end at the same fact (`GoogleIdentity`) even though they reach it
by different means (a code exchange vs. a token the browser already holds). `GoogleSignInCompleter`
(`google/`) is that shared sequence, extracted once a second real caller needed it, not before —
`ENGINEERING-PRINCIPLES.md`'s "A Strategy is extracted from a second real implementation, not
predicted from one" is exactly this rule, satisfied this time because both callers existed before
the interface did. Password still shares nothing with either — the comparison above still holds for
password vs. Google, only "Google vs. Google" changed.

**2026-09-02 — a third shared piece, this time across every method: `AuthenticationCompleter`.**
`SignInWithPasswordUseCase.SignIn` and `GoogleSignInCompleter.Default` both ended with the exact
same two lines once their own credential check succeeded: build a `Principal.User`, call
`sessions.issue(...)`. Adding second-factor support meant that ending needed a real decision —
issue directly, or check `SecondFactorMethodRegistry` first and create a `PendingAuthentication`
instead — and writing that decision twice, once per method, was the same duplication risk
`GoogleSignInCompleter` was extracted to avoid one section up. `AuthenticationCompleter` is that
decision, in one place; both password and Google sign-in now end by calling it with a bare
`UserId`, not by touching `SessionIssuer` themselves.

## Why `password/` is a package but `SessionIssuer` is not

Two use cases exist today, `RegisterWithPasswordUseCase` and `SignInWithPasswordUseCase`, and the
design plan lists ten more across two more sign-in methods (Google OAuth, Google Credential Manager)
and second-factor verification — a dozen use cases in total, one file each, per ADR-053's "one
action, one file" rule. Leaving all twelve flat in this layer would repeat exactly the mistake
`identity:domain` already made and had to undo (`domain/README.md`'s dated correction); grouping by
the method that owns each use case avoids repeating it before it happens, following the same
`AuthenticationMethod`/`SecondFactorMethod` split the design plan already uses to describe these
methods. `SessionIssuer` and `IssuedSession` stay outside any one method's package because every one
of those twelve use cases calls the same `SessionIssuer` once authentication succeeds — filing it
under `password/` because that happens to be the first method built would misname what it actually
is.

## Why `LoginAttempts` exists instead of depending on `platform:cache` directly

`modules.yaml`'s generic `application` layer may depend on `own:domain`, `own:contract`,
`platform:kernel`, `platform:events` and `any:contract` — no other `platform:*` module, on purpose.
`application` is business logic; `platform:cache`'s `Counter` is a caching technology; letting one
name the other would make `SignInWithPasswordUseCase.RateLimited` depend on how "how many times
recently" happens to be answered today, not on the fact that a rate limiter needs to know it at all.
`LoginAttempts` is `identity`'s own port, named for what its one caller needs — `failuresWithin`,
`recordFailure` — and its real implementation, `LoginAttemptsOverCounter`, lives in
`infrastructure`, where `platform:*` is allowed (`infrastructure/README.md`).

## A recovered failure is logged here, not in `infrastructure`

`SignInWithPasswordUseCase.RateLimited` survives `LoginAttempts` throwing via `Fallback.orRecover` —
fail closed on the read (treat "can't tell how many attempts" as "already at the limit", ADR-074),
fail open on the write (a failed "record this failure" must not turn an honest wrong-password answer
into an unrelated 500). Both branches now log the exception they recover from, at `WARN`, right next
to the policy decision they describe — `ENGINEERING-PRINCIPLES.md`'s "A recovered failure is logged
where its meaning is known, not where it was thrown": an adapter only knows a technical operation
failed; this use case knows *why* that matters (a sign-in is being decided) and what was chosen in
response, so the same place that picks fail-open/fail-closed is where the log line belongs too.

This is a direct dependency on `org.slf4j.Logger` — the facade only, never `logback` — added to this
module's `build.gradle.kts` as a plain library coordinate, not a `platform:*` project reference.
`modules.yaml`'s layer allow-list governs which of this repository's own Gradle modules `application`
may depend on; `validateModuleGraph` compares only `ProjectDependency` edges, so a third-party library
coordinate like `slf4j-api` is not the kind of edge that rule was written to police — the same
category `kotlinx-coroutines-core` already sits in on this same file. `LoginAttemptsOverCounter`
(`infrastructure/README.md`) is unchanged: it still only throws, because it has no policy to log
about — see that file's own KDoc.

## Why `TokenFactory.Csprng` reaches for `SecureRandom` directly

A UUID (`platform:kernel`'s `IdGenerator`) publishes its own mint time and spends at most 74 bits
on randomness — ADR-047 already names that as unfit for anything that must be unguessable, which a
bearer token must be. `no-ambient-random` does not cover this: that rule keeps domain and
application code off *ambient* non-determinism reached for casually, and `Csprng` is the one
deliberate, reviewed place identity reaches for it on purpose, the same shape `Clock.Wall` already
has for wall-clock time. It reaches no technology beyond the JDK's own randomness source, which is
why it nests on the port instead of living in `infrastructure` (ADR-047).

## Why `TokenHasher.Hmac` verifies against exactly one pepper version

A token hashed under a superseded pepper will not verify once a rotation happens — a deliberately
deferred limit, not an oversight: nothing in this pass exercises rotation, so nothing yet needs the
hasher to check more than one. Where the pepper itself comes from — a configuration value today,
`identity.pepper_versions` once rotation is exercised for real — is a wiring decision for whoever
constructs this, not `Hmac`'s own concern. It reaches no technology beyond `javax.crypto`, the same
reason it nests here rather than in `infrastructure` (ADR-047).

## Why `CredentialRepository.findPasswordFor` is scoped to one `Credential` case

Not a generic `findFor(userId, kind)` keyed by a discriminator nothing yet needs to name — the same
choice `platform:cache` made against a general value cache before `Counter` had a second thing to
abstract over (ADR-074). A second `Credential` case gets its own accessor when it arrives, because
each is read a different way, not dispatched by a shared "kind" parameter nobody has asked this
port to carry yet.

## Why `GoogleOAuthGateway` and `GoogleIdTokenVerifier` are two ports, not one

Exchanging a code for tokens (a network call to Google) and verifying an ID token's signature (a
cryptographic check against a published key set) are two different reasons to change: the gateway
changes if Google's token endpoint contract changes, the verifier changes if the signing algorithm
or key-rotation handling does. `GoogleOAuthGatewayOverHttp` (`infrastructure`) depends on
`GoogleIdTokenVerifier` for exactly the piece it does not own — checking the token it gets back
really is genuine — the same separation `TokenFactory`/`TokenHasher` already model for this
module's own tokens.

## Why sign-in with Google refuses an email already taken by a different credential

`GoogleSignInCompleter` finds an existing account by Google `subject` alone, never by email — the
one lookup both Google methods share. If no account has that subject *and* the email is already
taken by some other credential —
a password account signing up again through Google, say — this refuses rather than silently
linking the two accounts. This is the safest default, not a settled product decision: silent
account linking by email is a real account-takeover shape (register a password account under a
victim's email first, then the victim's later Google sign-in gets folded into the attacker's
account) that deserves its own review before being allowed, not an assumption made in passing
(`backend/.plans/identity-implementation.md`, срез 6).

## `SessionIssuer`: a corrected first draft, no `TokenHasher` here yet

The design this class implements described the sequence as "mint a token pair, hash it, write the
session" and gave it a `TokenHasher` to do the hashing with. Building against that literally
stalled on one question neither the design nor `Session`'s own field list answers: *where* a hash
is written. `Session` carries no token or hash field (`domain/README.md`), and a hash written
nowhere is a hash computed for no reason. Validating a presented access token later, and detecting
a reused refresh token, both need a hash-indexed lookup that is a genuinely separate storage
concept from a `Session` row, and designing it belongs with the persistence slice that will build
`SessionStore`'s real implementation, not this one. `TokenFactory` mints the raw pair `SessionIssuer`
returns; hashing and persisting it for later validation is deferred, named here rather than
silently dropped.

## Why `SignInOutcome` exists, separate from `AuthenticationOutcome`

`SignIn` originally returned bare `AuthenticationOutcome` and never called `SessionIssuer` at all —
a "successful" sign-in produced no session and no tokens, a gap found only once something asked
what a caller was actually supposed to do with `Success(userId)`. `AuthenticationOutcome` cannot
grow to carry a session itself: it is a `domain` type, and `domain` may not see `IssuedSession`, an
`application` concept (`modules.yaml`). `SignInOutcome` is the two-case wrapper this needs —
`Issued(IssuedSession)` or `NotIssued(AuthenticationOutcome)` — reusing `AuthenticationOutcome`
unchanged for every failure reason rather than re-declaring `InvalidCredential`/`AccountDisabled`/
`RateLimited` a second time in a new hierarchy.

## `PendingAuthenticationStore`: the same "no real implementation yet" state `SessionStore` is in

`SessionStore` has stood as an interface with only a test fake behind it since срез 3, waiting for
the persistence slice to design the storage shape. `PendingAuthenticationStore` (2026-09-02) is in
that exact position now, for the same reason: `save`/`find`/`delete` are the whole contract
`AuthenticationCompleter` and `VerifySecondFactorUseCase` need, and neither needs to know whether
that ends up an Exposed table, a cache with a TTL, or something else.

## `SecondFactorMethod` shipped in two halves, for a real reason, not by accident

The verification half (`kind`, `isEnrolledFor`, `verify`) shipped first: it needs no encryption
decision, so it could be built and tested — against an empty registry, since nothing implemented
the port yet — before that decision existed. The enrollment half (`startEnrollment`,
`confirmEnrollment`) needed the author's answer on *how a TOTP secret is encrypted at rest* first,
asked as its own question rather than guessed — a security mechanism deserved a real decision, not
a default picked at 3 a.m. Once answered (Tink, AES-256-GCM), both halves became one port, both
implemented by `SecondFactorMethod.Rfc6238` — `backend/.plans/identity-implementation.md` has the
full account, including the question as it was actually asked.

## Why `SecretCipher` is reversible, when every other secret-comparing port here is not

`PasswordHasher` and `TokenHasher` both compare a presented value against a stored one and never
need the original back — Argon2id and HMAC are one-directional on purpose. TOTP breaks that
pattern: computing the current code requires the *raw* seed, so storing only a hash (as every other
secret in this module does) would make verification impossible. `SecretCipher` is this module's
first reversible-encryption port for exactly that reason, over Tink's AEAD rather than hand-rolled
`javax.crypto` — the same "audited library over self-written crypto" choice already made for JWT
verification (`nimbus-jose-jwt`).

`decrypt` throws rather than returning an outcome for a ciphertext that fails to authenticate: a
wrong password is an expected, routine branch of sign-in; a ciphertext that fails Tink's own
authentication tag is a corrupted row, a key rotated without re-encrypting, or tampering — not
something this module has a policy to choose between, unlike "wrong password" or "wrong TOTP code".

## `TotpEnrollmentStore`: the same "no real implementation yet" state `SessionStore` is in

Same reasoning `PendingAuthenticationStore`'s own README entry already gives: `save`/`find` are
the whole contract `SecondFactorMethod.Rfc6238` needs, and neither needs to know whether the real
implementation ends up an Exposed table or something else — that is the persistence slice's
decision, not this one's.

## `SecondFactorMethod.Rfc6238`: base32 and the TOTP math are their own tested classes

`secondfactor/totp/Base32` and `secondfactor/totp/Rfc6238Totp` exist separately from `Rfc6238`
itself so each can be checked against a published standard's own test vectors without needing a
`SecondFactorMethod`, a `UserRepository`, or a `TotpEnrollmentStore` in the way:
`Base32Spec` against RFC 4648 §10, `Rfc6238TotpSpec` against both RFC 4226 Appendix D (HOTP) and
RFC 6238 Appendix B (TOTP) — two independent RFCs' own vectors, not one checked against the other.
A bug in either would otherwise hide behind three collaborators' worth of setup before a test could
even reach it.

The enrollment URI it returns follows the `google/google-authenticator` wiki's own Key URI Format,
not an invented shape — `otpauth://totp/{issuer}:{email}?secret=...&issuer=...`, all three optional
parameters (`algorithm`, `digits`, `period`) left at their documented defaults (SHA1, 6, 30s) so
every authenticator app already assumes them without either side naming them. Percent-encoding the
label uses a hand-written RFC 3986 encoder rather than `java.net.URLEncoder`: that class encodes a
space as `+`, the `application/x-www-form-urlencoded` rule, which a URI's own query values do not
follow — a `+` there would reach an authenticator app as a literal plus sign, not a decoded space.

`verify` and `confirmEnrollment` both tolerate one 30-second step of clock drift either side of
`Clock.now()` — RFC 6238's own expectation of a verifier, not a margin this module invented.

## Two use cases for enrollment, where the design plan's own sketch named one

The design plan lists a single `EnrollSecondFactorUseCase.kt`, dispatching by kind, for the whole
enrollment flow. Building against that literally ran into ADR-053's "one action, one file" the
moment "start enrollment" (hand back a provisioning payload) and "confirm enrollment" (check a code,
activate) turned out to need different requests and different answers — the same shape of
correction already recorded for `SessionIssuer.issue`'s return type. `EnrollSecondFactorUseCase`
and `ConfirmSecondFactorEnrollmentUseCase` are the two actions that sketch's one name actually
needed.

## Understandable, scalable, extensible

A reader looking for "how does someone sign in with a password" finds `password/SignInWithPasswordUseCase.kt`
and its one decorator, not a use case competing for attention with a dozen others. Adding Google
Identity Services as a third method was a new `googlecredential/` package with its own use case,
fourteen lines long — `google/GoogleSignInCompleter` already existed to hold the part it shared
with `googleoauth/`, so the new package had nothing left to duplicate.

## Migration and fault tolerance

No schema. `SessionStore`, `UserRepository`, `CredentialRepository`, `PendingAuthenticationStore`,
`TotpEnrollmentStore` are interfaces only — their Postgres-backed implementations arrive with the
persistence slice, and nothing here changes when they do, because every use case depends on the
port, not on Postgres.

## The SOLID angle

**Single responsibility.** `RegisterWithPasswordUseCase` only registers; it does not also sign the
new user in — `identity/README.md`'s own reasoning for why `SessionIssuer` is not called from it.

**Open/closed.** `SignInWithPasswordUseCase.RateLimited` decorates `SignIn` without either class
knowing the other exists beyond the shared interface — the same Decorator shape `LlmProvider`'s
`Retrying`/`Caching`/`BudgetGuarded` already use elsewhere in this codebase.

**Liskov substitutability.** Any `SignInWithPasswordUseCase` — `SignIn`, `RateLimited`, or a test
fake — answers the same interface with the same contract: given a request, a `SignInOutcome` comes
back, never a thrown exception for an outcome the caller is expected to handle.

**Interface segregation.** Each port has exactly the methods its one real caller needs —
`LoginAttempts` has two, not a general-purpose cache API pressed into service.

**Dependency inversion.** Every use case depends on `UserRepository`/`CredentialRepository`/
`PasswordHasher`/`LoginAttempts`, never on Postgres, Argon2 or `platform:cache` directly.
