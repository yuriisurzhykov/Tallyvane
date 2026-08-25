@file:OptIn(ExperimentalUuidApi::class)

package tallyvane.platform.observability.log

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch
import tallyvane.platform.kernel.IdGeneratorFake
import kotlin.uuid.ExperimentalUuidApi

class TraceSpec :
    StringSpec(
        {
            "mints both identifiers through the port, in the shapes the standard names" {
                val trace = Trace.from(IdGeneratorFake())

                trace.traceId.value shouldHaveLength 32
                trace.spanId.value shouldHaveLength 16
                trace.traceId.value shouldMatch Regex("[0-9a-f]{32}")
                trace.spanId.value shouldMatch Regex("[0-9a-f]{16}")
            }

            "gives two requests different traces" {
                val ids = IdGeneratorFake()

                Trace.from(ids) shouldNotBe Trace.from(ids)
            }

            "takes the span from a UUID's random half, so two ids of the same millisecond differ" {
                val ids = IdGeneratorFake()
                val first = ids.next()
                val second = ids.next()

                first.toHexString().take(16) shouldBe second.toHexString().take(16)
                SpanId.from(first) shouldNotBe SpanId.from(second)
            }

            "refuses a trace id that is not 32 lowercase hex characters" {
                shouldThrow<IllegalArgumentException> { TraceId("4bf92f3577b34da6") }
                shouldThrow<IllegalArgumentException> { TraceId("4BF92F3577B34DA6A3CE929D0E0E4736") }
            }

            "refuses a span id that is not 16 lowercase hex characters" {
                shouldThrow<IllegalArgumentException> { SpanId("00f067aa") }
                shouldThrow<IllegalArgumentException> { SpanId("00F067AA0BA902B7") }
            }

            "refuses the all-zero identifiers the standard reserves as invalid" {
                shouldThrow<IllegalArgumentException> { TraceId("0".repeat(32)) }
                shouldThrow<IllegalArgumentException> { SpanId("0".repeat(16)) }
            }
        },
    )
