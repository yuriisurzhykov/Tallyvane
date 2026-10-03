package tallyvane.platform.events

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.reflect.KClass
import kotlin.time.Instant

private val MOMENT = Instant.parse("2026-10-03T09:00:00Z")

private class Happened(override val occurredAt: Instant = MOMENT) : DomainEvent

private class SomethingElse(override val occurredAt: Instant = MOMENT) : DomainEvent

private class Listening<E : DomainEvent>(
    override val eventType: KClass<E>,
    private val log: MutableList<String>,
    private val name: String,
) : EventSubscriber<E> {
    override fun on(event: E) {
        log += "$name heard ${event::class.simpleName}"
    }
}

class EventBusSpec :
    StringSpec(
        {
            "tells a subscriber the events of its kind" {
                val log = mutableListOf<String>()
                val bus = EventBus(listOf(Listening(Happened::class, log, "first")))

                bus.publish(Happened())

                log shouldBe listOf("first heard Happened")
            }

            "does not tell a subscriber the events of another kind" {
                val log = mutableListOf<String>()
                val bus = EventBus(listOf(Listening(Happened::class, log, "first")))

                bus.publish(SomethingElse())

                log shouldBe emptyList()
            }

            "tells every subscriber of the kind, in the order they were registered" {
                val log = mutableListOf<String>()
                val bus = EventBus(
                    listOf(Listening(Happened::class, log, "first"), Listening(Happened::class, log, "second")),
                )

                bus.publish(Happened())

                log shouldBe listOf("first heard Happened", "second heard Happened")
            }

            "lets a subscriber's failure reach the publisher, so the publisher's change is not kept" {
                val failing = object : EventSubscriber<Happened> {
                    override val eventType: KClass<Happened> = Happened::class

                    override fun on(event: Happened) = error("cannot answer")
                }

                shouldThrow<IllegalStateException> { EventBus(listOf(failing)).publish(Happened()) }
            }
        },
    )
