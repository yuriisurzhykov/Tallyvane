package tallyvane.platform.observability.log

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.MDC
import tallyvane.platform.kernel.Clock

/**
 * Local implementation: one JSON record per stdout line, captured by Docker's log driver.
 */
internal class ConsoleLogSink(private val clock: Clock) : LogSink {
    override fun emit(scope: String, service: String, record: LogRecord) {
        val attributes = buildJsonObject {
            MDC.getCopyOfContextMap()?.forEach { (key, value) -> put(key, value) }
            put("event.name", record.event)
            record.attributes.forEach { (key, value) -> put(key, value.toJsonValue()) }
        }
        val json = buildJsonObject {
            put("timestamp", clock.now().toString())
            put("service", service)
            put("scope", scope)
            put("severity", record.severity.name.lowercase())
            put("event", record.event)
            put("body", record.body ?: record.event)
            put("attributes", attributes)
            record.cause?.let { cause ->
                put(
                    "exception",
                    buildJsonObject {
                        put("type", cause.javaClass.name)
                        cause.message?.let { put("message", it) }
                        put("stacktrace", cause.stackTraceToString())
                    },
                )
            }
        }
        println(Json.encodeToString(json))
    }

    private fun Any.toJsonValue() = when (this) {
        is Number -> kotlinx.serialization.json.JsonPrimitive(this)
        is Boolean -> kotlinx.serialization.json.JsonPrimitive(this)
        else -> kotlinx.serialization.json.JsonPrimitive(toString())
    }
}
