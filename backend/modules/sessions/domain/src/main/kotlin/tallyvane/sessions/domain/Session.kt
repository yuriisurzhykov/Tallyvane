package tallyvane.sessions.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * What a person was given once they proved who they are: a record that a request carrying the right
 * secret is from them (ADR-079).
 *
 * It holds whose it is, how they proved it and when, and when it was last used, and answers one
 * question about them: whether it may still be used at a moment ([standingAt]). It does not know its
 * secret, which only its keeper does, and it cannot be changed: a session is replaced, never edited, so
 * that trust changing means a new secret.
 *
 * Not a `data class`, for the reason `Attempt` is not one: a generated `copy()` is public.
 *
 * Its state leaves through [writeTo] and comes back through [restore] (ADR-085), and through nothing else.
 */
public class Session private constructor(
    private val account: Uuid,
    private val factors: Set<Factor>,
    private val authenticatedAt: Instant,
    private val lastActiveAt: Instant,
) {
    init {
        require(factors.isNotEmpty()) { "A session records how its person proved who they are; none is no session." }
    }

    /**
     * Whether this session may be used at [now] under [lifetimes], or why it may not.
     *
     * Age is asked first: a session that is past its absolute lifetime is over however recently it was used.
     */
    public fun standingAt(now: Instant, lifetimes: Lifetimes): Standing = when {
        lifetimes.hasPassedSinceStart(authenticatedAt, now) -> Standing.EndedByAge()
        lifetimes.hasPassedSinceUse(lastActiveAt, now) -> Standing.EndedByIdleness()
        else -> Standing.Live(account, factors, authenticatedAt)
    }

    /**
     * This session, used at [now]. Use only moves forward: a [now] before the last use leaves it as it was.
     */
    public fun seenAt(now: Instant): Session = Session(account, factors, authenticatedAt, maxOf(lastActiveAt, now))

    /**
     * Tells [record] everything about this session.
     */
    public fun writeTo(record: Record) {
        record.session(account, authenticatedAt, lastActiveAt)
        Factor.entries.filter { it in factors }.forEach(record::proved)
    }

    override fun toString(): String = "Session(authenticatedAt=$authenticatedAt)"

    /**
     * Whoever keeps a session, told what it holds.
     */
    public interface Record {
        public fun session(account: Uuid, authenticatedAt: Instant, lastActiveAt: Instant)

        public fun proved(factor: Factor)
    }

    public companion object {
        /**
         * How coarsely last use is kept. A person who clicks around does not make the database write on
         * every request, and a day-long idle limit does not notice a minute.
         */
        public val USE_GRAIN: Duration = 1.minutes

        /**
         * A session for [account] who proved who they are by [factors], the last of them at
         * [authenticatedAt], begun at [now].
         *
         * A [now] before [authenticatedAt], which two clocks that disagree by milliseconds can make, is
         * taken as [authenticatedAt] (slice 3, fork 5).
         */
        public fun begin(account: Uuid, factors: Set<Factor>, authenticatedAt: Instant, now: Instant): Session =
            Session(account, factors.toSet(), authenticatedAt, maxOf(now, authenticatedAt))

        /**
         * The session [replay] describes, as storage kept it.
         *
         * @throws IllegalStateException the replay does not describe exactly one session, or describes one
         * that [writeTo] could not have told.
         */
        public fun restore(replay: (Record) -> Unit): Session = Restoration().also(replay).session()
    }

    /**
     * Collects a replay and checks that it is a session [writeTo] could have told. The lists are the
     * only mutable things here: `domain` has no `var`.
     */
    private class Restoration : Record {
        private val told = mutableListOf<Triple<Uuid, Instant, Instant>>()
        private val factors = mutableSetOf<Factor>()

        override fun session(account: Uuid, authenticatedAt: Instant, lastActiveAt: Instant) {
            told += Triple(account, authenticatedAt, lastActiveAt)
        }

        override fun proved(factor: Factor) {
            check(factors.add(factor)) { "A session was kept proved twice by $factor; writeTo tells each once." }
        }

        fun session(): Session {
            check(told.size == 1) { "Storage replayed ${told.size} sessions where one was kept." }
            val (account, begun, used) = told.single()
            check(factors.isNotEmpty()) {
                "A session was kept with no proof of who its person is; writeTo never tells that."
            }
            check(used >= begun) {
                "A session kept as last used ($used) before it began ($begun); writeTo never tells that."
            }
            return Session(account, factors.toSet(), begun, used)
        }
    }
}
