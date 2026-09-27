package tallyvane.platform.observability.log

/**
 * One structured event. [event] is a stable machine-readable name; [body] is the text an operator
 * reads. Attributes are scalar values so they map cleanly to JSON and OpenTelemetry.
 */
public data class LogRecord(
    public val severity: Severity,
    public val event: String,
    public val body: String? = null,
    public val attributes: Map<String, Any> = emptyMap(),
    public val cause: Throwable? = null,
) {
    init {
        require(event.isNotBlank()) { "A log event name cannot be blank" }
        require(attributes.values.all { value -> value is String || value is Number || value is Boolean }) {
            "Log attributes must be strings, numbers, or booleans"
        }
    }
}
