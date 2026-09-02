package tallyvane.identity.infrastructure.password

import de.mkammerer.argon2.Argon2Factory
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.platform.kernel.Secret

/**
 * Argon2id, via `argon2-jvm-nolibs` — no native binary bundled by this dependency; `libargon2-1`
 * is installed on the image instead (`backend/Dockerfile`), the maintainer's own recommended
 * shape. Verified against that exact image in `backend/playground/argon2/README.md`, because
 * neither this development machine nor CI's own runner carries the native library this class
 * calls through JNA.
 *
 * A top-level `internal` adapter, not nested on [PasswordHasher] the way [TokenFactory]/[TokenHasher]
 * nest on theirs: this reaches a third-party native library, not just the JDK, so ADR-047's
 * nesting exception does not cover it (`nested-impl-is-pure` would have to skip a name whose
 * whole point is that it *does* reach a driver).
 *
 * [memoryCostKib], [iterations] and [parallelism] have no defaults on purpose — the same choice
 * `TokenHasher.Hmac` already made for its pepper: a security parameter belongs in one visible
 * place the composition root supplies, not hidden inside the class that uses it. OWASP's current
 * minimum-recommended Argon2id profile — 19 MiB, 2 iterations, 1 degree of parallelism — is a
 * reasonable starting point for this server's modest budget (a 3 GB VPS shared with Postgres,
 * three Next.js processes and the tunnel), not a number this class decides for whoever wires it.
 */
internal class Argon2PasswordHasher(
    private val memoryCostKib: Int,
    private val iterations: Int,
    private val parallelism: Int,
) : PasswordHasher {
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    override fun hash(raw: Secret): PasswordHash {
        val chars = raw.revealed().toCharArray()
        try {
            return PasswordHash(Secret(argon2.hash(iterations, memoryCostKib, parallelism, chars)))
        } finally {
            argon2.wipeArray(chars)
        }
    }

    override fun verify(raw: Secret, hash: PasswordHash): Boolean {
        val chars = raw.revealed().toCharArray()
        try {
            return argon2.verify(hash.encoded.revealed(), chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }
}
