package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret

/**
 * The behaviour every [PasswordHasher] must show, whatever actually computes the hash.
 *
 * Written once and inherited by each implementation's spec: the fake in this module's own
 * `src/test`, and the real Argon2id adapter in `identity:infrastructure` — where its own spec
 * lives in `integrationTest`, not `test`, because it needs the native Argon2 library this
 * development environment and CI's own runner do not carry (see
 * `backend/playground/argon2/README.md` and `identity:infrastructure`'s own README for why that
 * spec is opt-in rather than part of `check`).
 */
public abstract class PasswordHasherConformance : StringSpec() {
    protected abstract fun fresh(): PasswordHasher

    init {
        "a password verifies against its own hash" {
            val hasher = fresh()
            val raw = Secret("correct horse battery staple")

            hasher.verify(raw, hasher.hash(raw)) shouldBe true
        }

        "a different password does not verify" {
            val hasher = fresh()

            hasher.verify(Secret("wrong password"), hasher.hash(Secret("correct password"))) shouldBe false
        }
    }
}
