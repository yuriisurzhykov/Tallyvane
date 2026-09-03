package tallyvane.identity.application.secondfactor.totp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Checked against two independent RFCs' own published test vectors, not against each other: RFC
 * 4226 Appendix D (HOTP, a counter) and RFC 6238 Appendix B (TOTP, the same math over a time
 * step) both use the ASCII secret `"12345678901234567890"` — if this class agreed with one and
 * disagreed with the other, that would be the sign the time-to-counter conversion, not the HOTP
 * core, was wrong.
 */
class Rfc6238TotpSpec :
    StringSpec({
        val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)

        // RFC 4226 Appendix D — HOTP(secret, count), 6 digits. Fed through codeAt by choosing a
        // time whose 30-second step equals count exactly: count * 30 seconds is always inside
        // step `count`'s own [0s, 30s) window relative to that step.
        "matches RFC 4226 Appendix D's own HOTP test vectors, driven through the time step" {
            val codesByCount = listOf(
                "755224", "287082", "359152", "969429", "338314",
                "254676", "287922", "162583", "399871", "520489",
            )
            val totp = Rfc6238Totp(digits = 6, period = 30.seconds)

            codesByCount.forEachIndexed { count, expected ->
                totp.codeAt(secret, Instant.fromEpochSeconds(count * THIRTY)) shouldBe expected
            }
        }

        // RFC 6238 Appendix B — TOTP(secret, time), 8 digits, the standard's own worked examples.
        "matches RFC 6238 Appendix B's own TOTP test vectors at eight digits" {
            val codesByTime = mapOf(
                59L to "94287082",
                1111111109L to "07081804",
                1111111111L to "14050471",
                1234567890L to "89005924",
                2000000000L to "69279037",
                20000000000L to "65353130",
            )
            val totp = Rfc6238Totp(digits = 8, period = 30.seconds)

            for ((time, expected) in codesByTime) {
                totp.codeAt(secret, Instant.fromEpochSeconds(time)) shouldBe expected
            }
        }

        "every code is padded to exactly its digit count, even when the truncated value is small" {
            // A sweep, not one hand-picked instant: `truncated % 10^6` is smaller than 100000 for
            // roughly one step in ten, so among a thousand consecutive steps several are certain
            // to need padStart's leading zeros, without this test asserting which ones.
            val totp = Rfc6238Totp(digits = 6, period = 30.seconds)

            val codes = (0 until STEPS_TO_SWEEP).map { step ->
                totp.codeAt(secret, Instant.fromEpochSeconds(step * THIRTY))
            }

            codes.forEach { code -> code.length shouldBe 6 }
        }
    }) {
    private companion object {
        const val THIRTY = 30L
        const val STEPS_TO_SWEEP = 1000L
    }
}
