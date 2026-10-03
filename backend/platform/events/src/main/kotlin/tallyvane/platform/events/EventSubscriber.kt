package tallyvane.platform.events

import kotlin.reflect.KClass

/**
 * A module's answer to an event of another.
 *
 * Runs inside the transaction of whoever published the event, so what it changes commits or rolls back
 * with the change that caused it. That is the reason a subscriber speaks to the same blocking ports as
 * a use case and opens no transaction of its own (ADR-052, ADR-090). A subscriber that throws takes the
 * publisher's whole change with it: a fact that cannot be answered is not allowed to stand.
 */
public interface EventSubscriber<E : DomainEvent> {
    /**
     * The kind of event this subscriber answers.
     */
    public val eventType: KClass<E>

    /**
     * Answers [event].
     */
    public fun on(event: E)
}
