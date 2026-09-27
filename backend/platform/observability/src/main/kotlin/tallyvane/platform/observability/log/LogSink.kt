package tallyvane.platform.observability.log

/**
 * Replaceable output implementation, chosen once at the environment's composition root.
 */
public fun interface LogSink {
    public fun emit(scope: String, service: String, record: LogRecord)
}
