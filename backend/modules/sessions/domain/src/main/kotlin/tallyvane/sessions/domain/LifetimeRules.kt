package tallyvane.sessions.domain

import kotlin.time.Duration

/**
 * The [Lifetimes] in force for every kind of client at one moment (ADR-079).
 *
 * Read as a whole, with one query, so a request pays once however many kinds of client there are, and
 * a session picks its own kind's. Whether each kind has lifetimes is checked when the rules are built,
 * so no session can meet a kind that has none.
 */
public class LifetimeRules private constructor(private val byClient: Map<ClientType, Lifetimes>) {
    internal fun of(client: ClientType): Lifetimes = byClient.getValue(client)

    override fun toString(): String = "LifetimeRules($byClient)"

    /**
     * Whoever keeps lifetimes, told which kind of client each is for.
     */
    public fun interface Record {
        public fun lifetimes(client: ClientType, idle: Duration, absolute: Duration)
    }

    public companion object {
        /**
         * The rules [replay] describes, as storage kept them.
         *
         * @throws IllegalStateException a kind of client is told twice or not at all.
         * @throws IllegalArgumentException a pair of lifetimes is outside the bounds.
         */
        public fun restore(replay: (Record) -> Unit): LifetimeRules = Restoration().also { replay(it) }.rules()
    }

    private class Restoration : Record {
        private val told = mutableMapOf<ClientType, Lifetimes>()

        override fun lifetimes(client: ClientType, idle: Duration, absolute: Duration) {
            check(told.put(client, Lifetimes(idle, absolute)) == null) {
                "Lifetimes were kept twice for $client; there is one active version for each."
            }
        }

        fun rules(): LifetimeRules {
            val missing = ClientType.entries - told.keys
            check(missing.isEmpty()) { "No lifetimes are in force for $missing." }
            return LifetimeRules(told.toMap())
        }
    }
}
