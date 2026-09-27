package tallyvane.platform.observability.log

/**
 * Application-facing interface for emitting one structured log record.
 */
public fun interface Logger {
    public fun emit(record: LogRecord)
}
