package tallyvane.platform.http

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.platform.kernel.IdGeneratorFake

private const val VALID_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736"

private const val VALID_SPAN = "00f067aa0ba902b7"

/**
 * The parser had no tests of its own until 2026-08-26 — it was exercised only through `ApiSpec`,
 * and only on a header that was entirely valid. A review claimed several malformed headers are
 * accepted; these cases are what settled which claims were true.
 */
class TraceHeaderSpec :
    StringSpec(
        {
            val header = TraceHeader(IdGeneratorFake())

            "a valid header is continued" {
                header.read("00-$VALID_TRACE-$VALID_SPAN-01").traceId.value shouldBe VALID_TRACE
            }

            "no header starts a fresh trace" {
                header.read(null).traceId.value shouldNotBe VALID_TRACE
            }

            "a non-hex trace id is refused, not continued" {
                val zzz = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"

                header.read("00-$zzz-$VALID_SPAN-01").traceId.value shouldNotBe zzz
            }

            "an all-zero trace id is refused" {
                val zeroes = "0".repeat(32)

                header.read("00-$zeroes-$VALID_SPAN-01").traceId.value shouldNotBe zeroes
            }

            "an all-zero parent span id makes the whole header invalid per W3C" {
                header.read("00-$VALID_TRACE-0000000000000000-01").traceId.value shouldNotBe VALID_TRACE
            }

            "a parent span id of the wrong length makes the whole header invalid" {
                header.read("00-$VALID_TRACE-00f067-01").traceId.value shouldNotBe VALID_TRACE
            }

            "non-hex flags make the whole header invalid" {
                header.read("00-$VALID_TRACE-$VALID_SPAN-zz").traceId.value shouldNotBe VALID_TRACE
            }
        },
    )
