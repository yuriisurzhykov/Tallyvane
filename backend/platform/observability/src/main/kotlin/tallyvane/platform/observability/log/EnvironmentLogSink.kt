package tallyvane.platform.observability.log

import tallyvane.platform.kernel.Clock

internal class EnvironmentLogSink(environment: Map<String, String>, clock: Clock) : LogSink {
    private val delegate: LogSink = when {
        environment["OTEL_SDK_DISABLED"]?.equals("true", ignoreCase = true) == true -> ConsoleLogSink(clock)
        environment["OTEL_LOGS_EXPORTER"]?.split(',')?.map(String::trim) == listOf("none") -> ConsoleLogSink(clock)
        else -> Slf4jLogSink()
    }

    override fun emit(scope: String, service: String, record: LogRecord) = delegate.emit(scope, service, record)
}
