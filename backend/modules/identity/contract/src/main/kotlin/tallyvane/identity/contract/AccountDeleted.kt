package tallyvane.identity.contract

import tallyvane.platform.events.DomainEvent
import kotlin.time.Instant

/**
 * An account was deleted (ADR-090).
 *
 * Published in the transaction that deletes it, so whoever answers, `sessions` first, changes what it
 * keeps for that person in the same commit: nothing is left acting for an account that is gone.
 *
 * Nothing publishes it yet, because there is no way to delete an account yet; the deletion this event
 * belongs to publishes it when it arrives.
 */
public class AccountDeleted(private val account: AccountId, override val occurredAt: Instant) : DomainEvent {
    /**
     * Tells [report] which account was deleted.
     */
    public fun reportTo(report: Report) {
        report.deleted(account)
    }

    override fun equals(other: Any?): Boolean =
        other is AccountDeleted && other.account == account && other.occurredAt == occurredAt

    override fun hashCode(): Int = 31 * account.hashCode() + occurredAt.hashCode()

    override fun toString(): String = "AccountDeleted($account)"

    /**
     * Whoever answers the event, told which account it was.
     */
    public fun interface Report {
        public fun deleted(account: AccountId)
    }
}
