package tallyvane.platform.observability.log

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import org.slf4j.MDC

// The two examples the W3C Trace Context specification itself uses.
private val alpha = Trace(TraceId("4bf92f3577b34da6a3ce929d0e0e4736"), SpanId("00f067aa0ba902b7"))

private val beta = Trace(TraceId("0af7651916cd43dd8448eb211c80319c"), SpanId("b7ad6b7169203331"))

private fun traceIdsIn(lines: List<String>): List<String?> = lines.map { line ->
    Json
        .parseToJsonElement(line)
        .jsonObject["mdc"]
        ?.jsonObject
        ?.get("trace_id")
        ?.jsonPrimitive
        ?.content
}

class TraceContextSpec :
    StringSpec(
        {
            "puts the trace into a line logged inside it" {
                val lines = logged { withContext(TraceContext(alpha)) { LoggerFactory.getLogger("probe").info("in") } }

                traceIdsIn(lines) shouldContainExactly listOf(alpha.traceId.value)
            }

            "keeps the trace across a suspension point, which a bare MDC does not" {
                val lines =
                    logged {
                        withContext(TraceContext(alpha)) {
                            LoggerFactory.getLogger("probe").info("before")
                            delay(1)
                            LoggerFactory.getLogger("probe").info("after delay")
                            withContext(Dispatchers.IO) {
                                LoggerFactory.getLogger("probe").info("on another thread")
                            }
                        }
                    }

                traceIdsIn(lines) shouldContainExactly List(3) { alpha.traceId.value }
            }

            "loses a bare MDC entry on a dispatcher hop, which is the reason this element exists" {
                val lines =
                    logged {
                        MDC.put("trace_id", alpha.traceId.value)
                        try {
                            LoggerFactory.getLogger("probe").info("same thread")
                            withContext(Dispatchers.IO) { LoggerFactory.getLogger("probe").info("other thread") }
                        } finally {
                            MDC.clear()
                        }
                    }

                traceIdsIn(lines) shouldContainExactly listOf(alpha.traceId.value, null)
            }

            "carries the span alongside the trace" {
                val lines = logged { withContext(TraceContext(alpha)) { LoggerFactory.getLogger("probe").info("in") } }

                Json
                    .parseToJsonElement(lines.single())
                    .jsonObject
                    .getValue("mdc")
                    .jsonObject
                    .getValue("span_id")
                    .jsonPrimitive
                    .content shouldBe alpha.spanId.value
            }

            "restores the outer trace when a nested one ends" {
                val lines =
                    logged {
                        withContext(TraceContext(alpha)) {
                            withContext(TraceContext(beta)) { LoggerFactory.getLogger("probe").info("inner") }
                            LoggerFactory.getLogger("probe").info("outer again")
                        }
                    }

                traceIdsIn(lines) shouldContainExactly listOf(beta.traceId.value, alpha.traceId.value)
            }

            "leaves the thread's MDC as it found it" {
                withContext(TraceContext(alpha)) { LoggerFactory.getLogger("probe").info("in") }

                MDC.get("trace_id").shouldBeNull()
                MDC.get("span_id").shouldBeNull()
            }

            "reads the current trace from code that needs the value rather than a log line" {
                withContext(TraceContext(alpha)) { TraceContext.current() shouldBe alpha }
            }

            "has no current trace outside a request" {
                TraceContext.current().shouldBeNull()
            }
        },
    )
