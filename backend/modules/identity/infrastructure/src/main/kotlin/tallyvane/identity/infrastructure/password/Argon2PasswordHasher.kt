package tallyvane.identity.infrastructure.password

import de.mkammerer.argon2.Argon2Factory
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.platform.kernel.Secret

/**
 * Argon2id, via `argon2-jvm-nolibs` — needs `libargon2-1` installed on the image
 * (`backend/Dockerfile`); verified against that exact image in
 * `backend/playground/argon2/README.md`.
 *
 * A top-level `internal` adapter, not nested on [PasswordHasher] — why, and why
 * [memoryCostKib]/[iterations]/[parallelism] have no defaults: `infrastructure/README.md`.
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
