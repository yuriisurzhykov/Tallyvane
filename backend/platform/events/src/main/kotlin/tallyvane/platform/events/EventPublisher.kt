package tallyvane.platform.events

/**
 * Tells every subscriber that something happened (ADR-090).
 *
 * Synchronous and in the caller's transaction: when [publish] returns, every subscriber has answered.
 */
public interface EventPublisher {
    /**
     * Hands [event] to each subscriber of its kind, in the order they were registered.
     */
    public fun publish(event: DomainEvent)
}
