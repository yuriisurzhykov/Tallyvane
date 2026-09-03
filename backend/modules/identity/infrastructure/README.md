# identity:infrastructure

The real adapters behind `application`'s ports: `Argon2PasswordHasher` (over `argon2-jvm-nolibs`),
`LoginAttemptsOverCounter` (over `platform:cache`'s `Counter`), `GoogleOAuthGatewayOverHttp`
(`googleoauth/`, over Ktor's HTTP client), `GoogleIdTokenVerifierOverJwks` (`google/`, over
Nimbus JOSE+JWT) and `TinkSecretCipher` (`secondfactor/`, over Google's Tink). One Gradle module,
not one per method — `password/`, `googleoauth/`, `google/` and `secondfactor/` here are plain
packages, the same way `application`'s own method packages are (`application/README.md`), not
separate Gradle modules.

`GoogleIdTokenVerifierOverJwks` sits in `google/`, not `googleoauth/`, because Google Identity
Services (`googlecredential/` in `application`) verifies a token through this exact same adapter
and needs no HTTP call of its own — the browser's own Credential Manager already handed the client
a token, so that method has no gateway to write at all. A package named for one method would have
made the second method's only dependency look like it was reaching into the first method's own
territory, when it is really reaching into shared Google-token-verification territory that happens
to have only one Google method's HTTP gateway next to it today.

## Why this is one Gradle module, not one per authentication method

The design plan's original diagram for `identity` showed one Gradle module per authentication
method inside `infrastructure`, by analogy with `capture`'s own module-per-source split.
`ARCHITECTURE.md` names that split as `capture`'s specific exception (ADR-073), not a rule every
module follows — the plan's diagram was inaccurate by analogy, not a decision made for `identity`
itself. `identity:infrastructure` stays one module; Google's own adapters (its HTTP client, its
JWKS cache) land in sibling packages — `googleoauth/`, `google/` — next to `password/`.

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

## Why signature verification is a real dependency, not hand-rolled

The OIDC Core 1.0 spec technically allows trusting TLS instead of checking a signature when an ID
token arrives over a direct, authenticated channel — the Authorization Code flow's back-channel
token exchange qualifies. Google's own documentation and general practice still say to check the
signature regardless: hand-rolled JWT verification is a real vulnerability surface (algorithm
confusion among the well-known ones), and `com.nimbusds:nimbus-jose-jwt` is the standard, audited
choice for the JVM rather than code written for this one adapter.

`GoogleIdTokenVerifierOverJwks`'s own tests do not mock Nimbus: the spec generates a real RSA key
pair, signs a real JWT, serves it from an in-process HTTP server on loopback, and checks the
adapter against that server — a wrong audience, a wrong issuer, an expired token, an unverified
email, and a token signed by a key absent from the published set are each verified as rejected, for
real, not asserted against a stand-in for the library.

## `TinkSecretCipher`: a keyset it reads, never one it generates

Constructed from a serialized Tink keyset handed in as configuration — the same category as
`TokenHasher.Hmac`'s pepper, a value this class reads rather than mints. Generating a fresh keyset
(`KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)`, serialized with
`TinkJsonProtoKeysetFormat.serializeKeyset`) is a one-time operational step this class deliberately
does not perform, because Tink's own documentation names mixing key generation with key use as a
mistake, not a convenience.

`TinkSecretCipherSpec` runs real AES-256-GCM, not a mock of Tink: a value survives an
encrypt/decrypt round trip unchanged, the same plaintext produces a different ciphertext on every
call (a fresh nonce, not a deterministic one), a ciphertext decrypted under a different key throws
rather than silently returning garbage, and a single flipped bit in a genuine ciphertext fails
authentication rather than decrypting to something wrong-but-plausible.

`getPrimitive(RegistryConfiguration.get(), Aead::class.java)`, not the single-argument overload
most published examples still show: Tink 1.23.0 marks that overload deprecated, found by the
build's own `-Werror` failing on a real deprecation warning, not by reading a changelog in advance.

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
fail in (`platform:cache/README.md`). `TinkSecretCipher`'s keyset is configuration for the same
reason: whoever constructs it supplies the serialized keyset, generated once by an operational step
outside this codebase's own runtime path.

## The SOLID angle

**Single responsibility.** `Argon2PasswordHasher` only hashes and verifies; it does not decide
memory cost or read it from configuration itself.

**Liskov substitutability.** `LoginAttemptsOverCounter` implements the whole of `LoginAttempts` with
the exact semantics its callers expect — including "throws when the underlying store is
unavailable", not a swallowed default that would silently misrepresent the count.

**Dependency inversion.** This layer depends on `Counter` and `Argon2Factory`, never the other way
around; `application` never sees either.
