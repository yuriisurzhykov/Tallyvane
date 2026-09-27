package tallyvane.platform.observability.log

import tallyvane.platform.kernel.Clock

/**
 * Binds the shared [Logger] interface to one replaceable output implementation.
 */
public class LoggerFactory(private val sink: LogSink, private val service: String) {
    /**
     * Chooses the process output at composition time and keeps the selected sink for the runtime.
     */
    public constructor(environment: Map<String, String> = System.getenv(), clock: Clock) : this(
        EnvironmentLogSink(environment, clock),
        environment["OTEL_SERVICE_NAME"] ?: "tallyvane-server",
    )

    public fun getLogger(scope: String): Logger = Logger { record -> sink.emit(scope, service, record) }
}
