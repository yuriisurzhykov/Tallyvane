# identity:infrastructure

The real adapters behind `application`'s ports: `Argon2PasswordHasher` (over `argon2-jvm-nolibs`),
`LoginAttemptsOverCounter` (over `platform:cache`'s `Counter`). One Gradle module, not one per
method — `password/` here is a plain package, the same way `application`'s own `password/` is
(`application/README.md`), not a second Gradle module.

## Why this is one Gradle module, not one per authentication method

The design plan's original diagram for `identity` showed one Gradle module per authentication
method inside `infrastructure`, by analogy with `capture`'s own module-per-source split.
`ARCHITECTURE.md` names that split as `capture`'s specific exception (ADR-073), not a rule every
module follows — the plan's diagram was inaccurate by analogy, not a decision made for `identity`
itself. `identity:infrastructure` stays one module; a second method's adapters (Google's own HTTP
client, its own JWKS cache) land in a sibling package, e.g. `google/`, next to `password/`.

## Why `LoginAttemptsOverCounter` is the one file allowed to see `Counter`

`modules.yaml` grants `infrastructure` `"platform:*"` — every platform module, not a named subset —
which is what lets this one file import `platform:cache`'s `Counter` while `application` cannot
(`application/README.md`). It is written as a thin, faithful relay on purpose: it does not decide
what a counter failure *means* — fail open or fail closed — because that decision belongs to
`SignInWithPasswordUseCase.RateLimited`, one layer up; a port should report what happened, not
pre-empt the policy for whoever reads it. That includes logging: this file does not catch, so it does
not log either — `ENGINEERING-PRINCIPLES.md`'s "A recovered failure is logged where its meaning is
known, not where it was thrown" puts the one log line this path produces in `RateLimited`, next to
the fail-open/fail-closed decision it explains (`application/README.md`).

## Why Argon2id needs its own Docker step, and how that was verified

`argon2-jvm-nolibs` ships no native binary; `libargon2-1` is installed on the image instead
(`backend/Dockerfile`), the maintainer's own recommended shape. This was verified against the exact
production base image, not assumed: `backend/playground/argon2/README.md` records a spike that ran
real hashing and verification inside that image before this adapter was trusted, because neither
this development machine nor CI's own runner carries the native library `Argon2PasswordHasher` calls
through JNA.

## Why `Argon2PasswordHasher` is a top-level class, not nested on `PasswordHasher`

Unlike `TokenFactory.Csprng`/`TokenHasher.Hmac`, this reaches a third-party native library, not
just the JDK, so ADR-047's nesting exception does not cover it — `nested-impl-is-pure` would have
to skip a name whose whole point is that it *does* reach a driver. It is a top-level `internal`
adapter instead, named by mechanism, the same shape `PostgresJobs` would take for a database.

## Understandable, scalable, extensible

A reader looking for "what actually hashes a password" finds one file reaching one third-party
dependency, not a generic adapter layer trying to cover every possible technology. A second
`PasswordHasher` — bcrypt, if Argon2id were ever replaced — is a new top-level `internal` class next
to this one; nothing that calls `PasswordHasher` changes, since every caller depends on the
interface.

## Migration and fault tolerance

No schema; this layer owns no table. `Argon2PasswordHasher`'s memory cost, iterations and
parallelism have no defaults — the composition root supplies them, the same choice `TokenHasher.Hmac`
already made for its pepper, so a security parameter lives in one visible place, not hidden inside
the class that uses it. OWASP's current minimum-recommended Argon2id profile — 19 MiB, 2
iterations, 1 degree of parallelism — is a reasonable starting point for this server's modest
budget (a 3 GB VPS shared with Postgres, three Next.js processes and the tunnel), not a number this
class decides for whoever wires it. `LoginAttemptsOverCounter` today sits over `Counter.InMemory`,
which loses every window's count on a crash or restart — the safe direction for a rate limiter to
fail in (`platform:cache/README.md`).

## The SOLID angle

**Single responsibility.** `Argon2PasswordHasher` only hashes and verifies; it does not decide
memory cost or read it from configuration itself.

**Liskov substitutability.** `LoginAttemptsOverCounter` implements the whole of `LoginAttempts` with
the exact semantics its callers expect — including "throws when the underlying store is
unavailable", not a swallowed default that would silently misrepresent the count.

**Dependency inversion.** This layer depends on `Counter` and `Argon2Factory`, never the other way
around; `application` never sees either.
