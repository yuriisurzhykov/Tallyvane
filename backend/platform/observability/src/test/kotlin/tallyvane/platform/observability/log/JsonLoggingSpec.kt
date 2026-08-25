package tallyvane.platform.observability.log

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

class JsonLoggingSpec :
    StringSpec(
        {
            "writes one line per event, and each line is valid JSON" {
                val lines =
                    logged {
                        LoggerFactory.getLogger("probe").info("first")
                        LoggerFactory.getLogger("probe").warn("second")
                    }

                lines.size shouldBe 2
                lines.forEach { line -> Json.parseToJsonElement(line).jsonObject }
            }

            "fills the message's placeholders instead of emitting a template and a list" {
                val lines = logged { LoggerFactory.getLogger("probe").info("captured {} jobs", 7) }

                val line = Json.parseToJsonElement(lines.single()).jsonObject
                line.getValue("formattedMessage").jsonPrimitive.content shouldBe "captured 7 jobs"
                line.keys shouldNotContainAnyOf listOf("message", "arguments")
            }

            "keeps the members an operator reads and drops the ones nobody does" {
                val lines = logged { LoggerFactory.getLogger("probe").info("anything") }

                val line = Json.parseToJsonElement(lines.single()).jsonObject
                line.keys shouldContainAll listOf("timestamp", "level", "loggerName", "formattedMessage")
                line.keys shouldNotContainAnyOf listOf("sequenceNumber", "nanoseconds", "context")
            }

            "reports the level as a name, so a threshold in an alert reads as one" {
                val lines = logged { LoggerFactory.getLogger("probe").warn("needs attention") }

                Json
                    .parseToJsonElement(lines.single())
                    .jsonObject
                    .getValue("level")
                    .jsonPrimitive
                    .content shouldBe "WARN"
            }
        },
    )
