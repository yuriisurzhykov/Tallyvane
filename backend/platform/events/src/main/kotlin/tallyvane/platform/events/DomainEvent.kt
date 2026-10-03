package tallyvane.platform.events

import kotlin.time.Instant

/**
 * Something that happened in one module and that another may need to answer (ADR-090).
 *
 * A fact in the past tense, named for what happened (`AccountDeleted`), carrying what a listener needs to
 * react and no more. It lives in the contract layer of the module that publishes it, so a module that
 * listens reads that contract and nothing else of the publisher.
 */
public interface DomainEvent {
    /**
     * When it happened.
     */
    public val occurredAt: Instant
}
