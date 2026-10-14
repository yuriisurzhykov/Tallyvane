package tallyvane.sessions.domain

import kotlin.time.Duration

/**
 * One numbered version of the lifetimes of a kind of client (ADR-078).
 *
 * A change never edits lifetimes; it makes the next version, and one version at a time is in force for each kind
 * of client. The number is what a rollback names.
 *
 * Its state leaves through [writeTo] and comes back through [restore] (ADR-085). It is not a `data class`, which
 * would publish a `copy` that makes a version with a number nobody assigned.
 *
 * @param number Counts from 1 within one kind of client.
 * @param lifetimes The checked lifetimes this version holds.
 */
public class LifetimeVersion(private val number: Int, private val lifetimes: ClientLifetimes) {
    init {
        require(number >= 1) {
            "A lifetime version is numbered from 1, but was given $number. " +
                "Storage assigns the number when it keeps a version; do not make one up."
        }
    }

    /**
     * The rules in force with this version's lifetimes in place of the ones [rules] has for its kind of client.
     */
    public fun beside(rules: LifetimeRules): LifetimeRules = lifetimes.beside(rules)

    /**
     * Tells [record] the number, then everything the lifetimes say about themselves.
     */
    public fun writeTo(record: Record) {
        record.number(number)
        lifetimes.writeTo(record)
    }

    override fun toString(): String = "LifetimeVersion(number=$number, lifetimes=$lifetimes)"

    /**
     * What a version tells whoever keeps it, and what that keeper tells [restore] to bring it back. The number
     * first, then the lifetimes' own words: the kind of client, the three numbers.
     */
    public interface Record : ClientLifetimes.Record {
        /**
         * The version is numbered [number]. Always the first thing said.
         */
        public fun number(number: Int)
    }

    public companion object {
        /**
         * The version a keeper [replay]s into the [Record] it is handed, in the order [writeTo] says things.
         *
         * The number comes back as it was; the lifetimes are checked against the bounds the code has *today*.
         *
         * @throws IllegalStateException for a replay that is incomplete, or describes lifetimes that break a bound.
         */
        public fun restore(replay: (Record) -> Unit): LifetimeVersion = Restoration().also(replay).version()
    }

    private class Restoration : Record {
        private val numbers = mutableListOf<Int>()
        private val clients = mutableListOf<ClientType>()
        private val limits = mutableListOf<Triple<Duration, Duration, Duration>>()

        override fun number(number: Int) {
            numbers += number
        }

        override fun client(client: ClientType) {
            clients += client
        }

        override fun lifetimes(idle: Duration, absolute: Duration, freshness: Duration) {
            limits += Triple(idle, absolute, freshness)
        }

        fun version(): LifetimeVersion {
            val number = checkNotNull(numbers.singleOrNull()) { refused("it has no single number") }
            val client = checkNotNull(clients.singleOrNull()) { refused("it has no single kind of client") }
            val (idle, absolute, freshness) = checkNotNull(limits.singleOrNull()) {
                refused("it has no single set of lifetimes")
            }
            return LifetimeDraft(client, idle, absolute, freshness).check().reportTo(Judgement(number))
        }

        private fun refused(reason: String): String =
            "A stored lifetime version cannot be restored: $reason. LifetimeVersion.writeTo always says all of " +
                "it, so the stored rows were changed by something else. Versions are never edited; restore the " +
                "rows from a backup or add a new version and activate that one."

        private class Judgement(private val number: Int) : LifetimeCheck.Report<LifetimeVersion> {
            override fun passed(lifetimes: ClientLifetimes): LifetimeVersion = LifetimeVersion(number, lifetimes)

            override fun rejected(violations: List<LifetimeViolation>): LifetimeVersion = error(
                "Stored lifetime version $number breaks the bounds the code sets today: $violations. " +
                    "The bounds were narrowed after it was saved. Activate another version, or " +
                    "add one within the bounds and activate that.",
            )
        }
    }
}
