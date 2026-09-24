package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import tallyvane.platform.kernel.Secret

/** Shared properties required of every authentication-code generator and hasher. */
public abstract class AuthenticationCodesConformance : StringSpec() {
    protected abstract fun fresh(): AuthenticationCodes

    init {
        "email codes contain exactly six decimal digits" {
            fresh().emailCode().revealed() shouldMatch Regex("^[0-9]{6}$")
        }

        "backup codes are long and URL-safe" {
            val code = fresh().backupCode().revealed()
            code.length shouldBe 22
            code shouldMatch Regex("^[A-Za-z0-9_-]+$")
        }

        "hashing is deterministic and purpose-separated" {
            val codes = fresh()
            val code = Secret("123456")
            codes.hash("registration", code) shouldBe codes.hash("registration", code)
            (codes.hash("registration", code) == codes.hash("sign-in", code)) shouldBe false
        }
    }
}
