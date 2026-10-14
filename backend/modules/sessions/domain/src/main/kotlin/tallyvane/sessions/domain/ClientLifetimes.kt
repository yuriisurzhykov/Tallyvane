package tallyvane.sessions.domain

import kotlin.time.Duration

/**
 * The lifetimes one kind of client lives by (ADR-079): a [Lifetimes] that is checked and knows whose it is.
 *
 * Only [LifetimeDraft.check] and a replay of storage make one, so a pair of lifetimes outside the bounds the code
 * sets is not merely refused at the door; it cannot be built. Its state leaves through [writeTo] only.
 */
public class ClientLifetimes internal constructor(private val client: ClientType, private val lifetimes: Lifetimes) {
    /**
     * Tells [record] which kind of client these are for, then the three numbers.
     */
    public fun writeTo(record: Record) {
        record.client(client)
        lifetimes.writeTo(record)
    }

    /**
     * The rules in force with these lifetimes in place of the ones [rules] has for this kind of client.
     */
    public fun beside(rules: LifetimeRules): LifetimeRules = rules.with(client, lifetimes)

    override fun toString(): String = "ClientLifetimes(client=$client, lifetimes=$lifetimes)"

    /**
     * What lifetimes tell whoever keeps them.
     */
    public interface Record {
        /**
         * The lifetimes are for the kind of client [client]. Always the first thing said.
         */
        public fun client(client: ClientType)

        public fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration)
    }
}
