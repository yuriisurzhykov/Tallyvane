package tallyvane.authentication.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import tallyvane.authentication.domain.FactorKind.Google
import tallyvane.authentication.domain.FactorKind.RecoveryCode
import tallyvane.authentication.domain.FactorKind.Totp
import kotlin.time.Instant

private val AT = Instant.parse("2026-10-02T09:00:00Z")

class VerifiedFactorSpec :
    StringSpec(
        {
            "Google identifies the account, so it is made with whose it is" {
                VerifiedFactor.identifying(Google, "sub-1", AT) shouldBe VerifiedFactor.identifying(Google, "sub-1", AT)
            }

            "a factor that needs setting up only confirms, so it cannot name an account" {
                shouldThrow<IllegalArgumentException> { VerifiedFactor.identifying(Totp, "sub-1", AT) }
                shouldThrow<IllegalArgumentException> { VerifiedFactor.identifying(RecoveryCode, "sub-1", AT) }
            }

            "Google cannot be recorded without saying whose it is" {
                shouldThrow<IllegalArgumentException> { VerifiedFactor.confirming(Google, AT) }
            }

            "a provider that names nobody is refused" {
                shouldThrow<IllegalArgumentException> { VerifiedFactor.identifying(Google, " ", AT) }
            }

            "two factors for two people are told apart, and a confirming factor disagrees with nobody" {
                val first = VerifiedFactor.identifying(Google, "sub-1", AT)

                first.disagreesWith(VerifiedFactor.identifying(Google, "sub-2", AT)) shouldBe true
                first.disagreesWith(VerifiedFactor.identifying(Google, "sub-1", AT)) shouldBe false
                first.disagreesWith(VerifiedFactor.confirming(Totp, AT)) shouldBe false
            }

            "a factor printed in a log does not name the account" {
                VerifiedFactor.identifying(Google, "sub-1", AT).toString() shouldNotContain "sub-1"
            }
        },
    )
