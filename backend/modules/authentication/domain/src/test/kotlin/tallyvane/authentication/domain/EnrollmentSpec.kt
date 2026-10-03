package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Digest
import kotlin.time.Instant

private val ENTROPY = "12345678901234567890".toByteArray(Charsets.US_ASCII)
private val AT = Instant.fromEpochSeconds(45)

private fun activeTotp(): TotpEnrollment = TotpEnrollment.begin(ENTROPY).confirm("287082", AT).reportTo(
    object : CodeVerdict.Report<TotpEnrollment> {
        override fun accepted(next: TotpEnrollment): TotpEnrollment = next

        override fun wrong(): TotpEnrollment = error("The code should have been accepted.")
    },
)

private fun codes(count: Int): RecoveryCodes =
    RecoveryCodes.issue((1..count).map { Digest(byteArrayOf(it.toByte(), 1), 1) })

class EnrollmentSpec :
    StringSpec(
        {
            "an account with nothing set up has nothing enrolled" {
                Enrollment.of(null, null) shouldBe Enrollment.Unknown
            }

            "an active seed with its codes enrols both kinds" {
                val enrolment = Enrollment.of(activeTotp(), codes(10))

                enrolment.includes(FactorKind.Totp) shouldBe true
                enrolment.includes(FactorKind.RecoveryCode) shouldBe true
                enrolment.includes(FactorKind.Google) shouldBe false
            }

            "a pending seed counts for nothing" {
                Enrollment.of(TotpEnrollment.begin(ENTROPY), null) shouldBe Enrollment.Unknown
            }

            "a retired seed leaves only the recovery codes" {
                val enrolment = Enrollment.of(activeTotp().retired(), codes(3))

                enrolment.includes(FactorKind.Totp) shouldBe false
                enrolment.includes(FactorKind.RecoveryCode) shouldBe true
            }

            "when the last code is spent under a retired seed, nothing is enrolled" {
                val spent = codes(1).spend(Digest(byteArrayOf(1, 1), 1), AT).reportTo(
                    object : SpendVerdict.Report<RecoveryCodes> {
                        override fun spent(next: RecoveryCodes): RecoveryCodes = next

                        override fun unknown(): RecoveryCodes = error("The code should have been spent.")
                    },
                )

                Enrollment.of(activeTotp().retired(), spent) shouldBe Enrollment.Unknown
            }
        },
    )
