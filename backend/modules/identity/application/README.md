# identity:application

Ports and use cases — the layer that turns `domain`'s types into something a caller can actually
invoke: register, sign in, issue a session.

```
port/       TokenFactory, TokenHasher, SessionStore, PasswordHasher, UserRepository,
            CredentialRepository, LoginAttempts
password/   RegisterWithPasswordUseCase, SignInWithPasswordUseCase (+ their requests)
SessionIssuer.kt, IssuedSession.kt   — shared by every sign-in path, not one method's own file
```

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

## Understandable, scalable, extensible

A reader looking for "how does someone sign in with a password" finds `password/SignInWithPasswordUseCase.kt`
and its one decorator, not a use case competing for attention with eleven others that do not exist
yet. Adding Google OAuth as a second method is a new `google-oauth/` package and a new
`AuthenticationMethod` case, not a change to `password/` or to `SessionIssuer`.

## Migration and fault tolerance

No schema. `SessionStore`, `UserRepository`, `CredentialRepository` are interfaces only — their
Postgres-backed implementations arrive with the persistence slice, and nothing here changes when they
do, because every use case depends on the port, not on Postgres.

## The SOLID angle

**Single responsibility.** `RegisterWithPasswordUseCase` only registers; it does not also sign the
new user in — `identity/README.md`'s own reasoning for why `SessionIssuer` is not called from it.

**Open/closed.** `SignInWithPasswordUseCase.RateLimited` decorates `SignIn` without either class
knowing the other exists beyond the shared interface — the same Decorator shape `LlmProvider`'s
`Retrying`/`Caching`/`BudgetGuarded` already use elsewhere in this codebase.

**Liskov substitutability.** Any `SignInWithPasswordUseCase` — `SignIn`, `RateLimited`, or a test
fake — answers the same interface with the same contract: given a request, an `AuthenticationOutcome`
comes back, never a thrown exception for an outcome the caller is expected to handle.

**Interface segregation.** Each port has exactly the methods its one real caller needs —
`LoginAttempts` has two, not a general-purpose cache API pressed into service.

**Dependency inversion.** Every use case depends on `UserRepository`/`CredentialRepository`/
`PasswordHasher`/`LoginAttempts`, never on Postgres, Argon2 or `platform:cache` directly.
