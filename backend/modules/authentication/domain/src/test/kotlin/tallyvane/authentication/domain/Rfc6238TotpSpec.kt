package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Checked against two RFCs' own published vectors, not against each other: RFC 4226 appendix D (HOTP, a
 * counter) and RFC 6238 appendix B (TOTP, the same arithmetic over a time step) use the secret
 * `12345678901234567890`.
 */
class Rfc6238TotpSpec :
    StringSpec(
        {
            val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)
            val hotp = listOf(
                "755224", "287082", "359152", "969429", "338314",
                "254676", "287922", "162583", "399871", "520489",
            )

            "it gives RFC 4226's codes at the time of each counter" {
                hotp.forEachIndexed { counter, expected ->
                    Rfc6238Totp().codeAt(secret, Instant.fromEpochSeconds(counter * STEP)) shouldBe expected
                }
            }

            "it gives RFC 6238's eight-digit codes" {
                val totp = Rfc6238Totp(digits = 8, period = 30.seconds)
                val vectors = mapOf(59L to "94287082", 1111111109L to "07081804", 20000000000L to "65353130")
                vectors.forEach { (time, expected) ->
                    totp.codeAt(secret, Instant.fromEpochSeconds(time)) shouldBe expected
                }
            }

            "a code is always as long as its digits, whatever the truncated number" {
                (0L until SWEEP).forEach { step ->
                    Rfc6238Totp().codeAt(secret, Instant.fromEpochSeconds(step * STEP)).length shouldBe 6
                }
            }

            "a code is found at its own step and one either side, and not beyond" {
                val code = hotp[3]
                val at = { step: Long -> Instant.fromEpochSeconds(step * STEP) }

                Rfc6238Totp().matchingStep(secret, code, at(3), 1, null) shouldBe 3L
                Rfc6238Totp().matchingStep(secret, code, at(2), 1, null) shouldBe 3L
                Rfc6238Totp().matchingStep(secret, code, at(4), 1, null) shouldBe 3L
                Rfc6238Totp().matchingStep(secret, code, at(5), 1, null) shouldBe null
                Rfc6238Totp().matchingStep(secret, code, at(1), 1, null) shouldBe null
            }

            "a step at or before the last accepted one is not found again" {
                val code = hotp[3]
                val now = Instant.fromEpochSeconds(3 * STEP)

                Rfc6238Totp().matchingStep(secret, code, now, 1, 3L) shouldBe null
                Rfc6238Totp().matchingStep(secret, code, now, 1, 4L) shouldBe null
                Rfc6238Totp().matchingStep(secret, code, now, 1, 2L) shouldBe 3L
            }

            "a code of the wrong shape is never found" {
                Rfc6238Totp().matchingStep(secret, "12345", Instant.fromEpochSeconds(0), 1, null) shouldBe null
                Rfc6238Totp().matchingStep(secret, "", Instant.fromEpochSeconds(0), 1, null) shouldBe null
            }
        },
    ) {
    private companion object {
        const val STEP = 30L
        const val SWEEP = 1000L
    }
}
