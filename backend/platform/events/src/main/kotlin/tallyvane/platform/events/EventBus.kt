package tallyvane.platform.events

/**
 * The publisher the application runs on: a list of subscribers, asked in turn, in this process.
 *
 * There is one process and one database (ADR-079), so an event is a method call in the transaction that
 * published it and nothing is lost between the fact and its answer. If the modules ever run apart, the
 * [EventPublisher] port stays and this class is what is replaced.
 */
public class EventBus(private val subscribers: List<EventSubscriber<*>>) : EventPublisher {
    override fun publish(event: DomainEvent) {
        subscribers.forEach { it.hears(event) }
    }

    private fun <E : DomainEvent> EventSubscriber<E>.hears(event: DomainEvent) {
        val kind = eventType
        // `isInstance` has just said the cast holds, and the cast is the `Class`'s own check.
        if (kind.isInstance(event)) {
            on(kind.javaObjectType.cast(event))
        }
    }

    override fun toString(): String = "EventBus(subscribers=${subscribers.size})"
}
