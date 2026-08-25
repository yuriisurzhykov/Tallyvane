package tallyvane.platform.observability

/**
 * What one dependency, or the application as a whole, currently is.
 *
 * Three states rather than two because a failed optional dependency must be
 * visible without taking the application out of rotation: a dead LLM provider
 * stops extraction and nothing else.
 */
public sealed interface Health {
    public data object Up : Health

    /**
     * Serving, but something an operator should look at.
     */
    public data class Degraded(val cause: Ailment) : Health

    public data class Down(val cause: Ailment) : Health
}
