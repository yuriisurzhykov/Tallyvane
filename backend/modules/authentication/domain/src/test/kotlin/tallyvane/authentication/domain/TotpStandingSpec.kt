package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Digest
import kotlin.time.Instant

private val ENTROPY = "12345678901234567890".toByteArray(Charsets.US_ASCII)
private val AT = Instant.fromEpochSeconds(45)

private fun pending(): TotpEnrollment = TotpEnrollment.begin(ENTROPY)

private fun active(): TotpEnrollment = pending().confirm("287082", AT).reportTo(
    object : CodeVerdict.Report<TotpEnrollment> {
        override fun accepted(next: TotpEnrollment): TotpEnrollment = next

        override fun wrong(): TotpEnrollment = error("The code should have been accepted.")
    },
)

private fun codes(count: Int): RecoveryCodes =
    RecoveryCodes.issue((1..count).map { Digest(byteArrayOf(it.toByte(), 1), 1) })

private fun spentAll(set: RecoveryCodes, count: Int): RecoveryCodes = (1..count).fold(set) { left, number ->
    left.spend(Digest(byteArrayOf(number.toByte(), 1), 1), AT).reportTo(
        object : SpendVerdict.Report<RecoveryCodes> {
            override fun spent(next: RecoveryCodes): RecoveryCodes = next

            override fun unknown(): RecoveryCodes = error("The code should have been spent.")
        },
    )
}

private fun told(standing: TotpStanding): String = standing.reportTo(
    object : TotpStanding.Report<String> {
        override fun off(): String = "off"

        override fun active(codesLeft: Int): String = "active, $codesLeft"

        override fun retired(codesLeft: Int): String = "retired, $codesLeft"
    },
)

class TotpStandingSpec :
    StringSpec(
        {
            "an account with nothing set up is off" {
                told(TotpStanding.of(null, null)) shouldBe "off"
            }

            "a first seed that was begun and never confirmed is off" {
                told(TotpStanding.of(pending(), null)) shouldBe "off"
            }

            "a seed that works is active with the codes it has left" {
                told(TotpStanding.of(active(), codes(10))) shouldBe "active, 10"
            }

            "a retired seed is retired with the codes it has left" {
                told(TotpStanding.of(active().retired(), codes(7))) shouldBe "retired, 7"
            }

            "a retired seed with every code spent is still retired, with none left" {
                told(TotpStanding.of(active().retired(), spentAll(codes(2), 2))) shouldBe "retired, 0"
            }

            "a seed begun again over a retired one is retired, not off, while codes are kept beside it" {
                told(TotpStanding.of(pending(), codes(7))) shouldBe "retired, 7"
            }

            "a seed begun again after every code was spent is retired with none left" {
                told(TotpStanding.of(pending(), spentAll(codes(2), 2))) shouldBe "retired, 0"
            }
        },
    )
