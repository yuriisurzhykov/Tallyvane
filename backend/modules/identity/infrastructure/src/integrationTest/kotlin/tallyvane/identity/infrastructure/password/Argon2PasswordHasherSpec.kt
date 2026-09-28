package tallyvane.identity.infrastructure.password

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.PasswordHasherConformance
import tallyvane.platform.kernel.Secret

/**
 * Needs the real Argon2 native library `argon2-jvm-nolibs` calls through JNA — present on
 * `backend/Dockerfile`'s image (`libargon2-1`, apt-installed) and verified there in
 * `backend/playground/argon2/README.md`, but not on this development machine or on CI's own
 * runner. `integrationTest`, not `test`, for that reason — the same shape a real Postgres
 * adapter's spec already takes (`ADR-057`), a dependency outside the JVM this suite needs and
 * `check` does not carry.
 */
class Argon2PasswordHasherSpec : PasswordHasherConformance() {
    override fun fresh(): PasswordHasher =
        Argon2PasswordHasher(memoryCostKib = MEMORY_COST_KIB, iterations = ITERATIONS, parallelism = PARALLELISM)

    init {
        "hashing the same password twice yields two different encoded hashes, salted" {
            val hasher = fresh()
            val raw = Secret("correct horse battery staple")

            hasher.hash(raw).encoded shouldNotBe hasher.hash(raw).encoded
        }

        "the encoded hash names the argon2id variant" {
            val encoded = fresh().hash(Secret("correct horse battery staple")).encoded.revealed()

            encoded.startsWith("\$argon2id\$") shouldBe true
        }
    }

    private companion object {
        const val MEMORY_COST_KIB = 19 * 1024
        const val ITERATIONS = 2
        const val PARALLELISM = 1
    }
}
