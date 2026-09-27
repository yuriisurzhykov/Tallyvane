package tallyvane.platform.observability.log

import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/**
 * Production implementation: structured SLF4J records are bridged by the OpenTelemetry agent.
 */
internal class Slf4jLogSink : LogSink {
    override fun emit(scope: String, service: String, record: LogRecord) {
        val logger = LoggerFactory.getLogger(scope)
        val builder =
            when (record.severity) {
                Severity.TRACE -> logger.atLevel(Level.TRACE)
                Severity.DEBUG -> logger.atLevel(Level.DEBUG)
                Severity.INFO -> logger.atLevel(Level.INFO)
                Severity.WARN -> logger.atLevel(Level.WARN)
                Severity.ERROR, Severity.FATAL -> logger.atLevel(Level.ERROR)
            }
        builder.addKeyValue("service.name", service)
        builder.addKeyValue("otel.event.name", record.event)
        record.attributes.forEach { (key, value) -> builder.addKeyValue(key, value) }
        record.cause?.let(builder::setCause)
        builder.log(record.body ?: record.event)
    }
}
