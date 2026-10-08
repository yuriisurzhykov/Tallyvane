package tallyvane.sessions.domain

import tallyvane.platform.kernel.Surface
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * What a person was given once they proved who they are: a record that a request carrying the right
 * secret is from them (ADR-079).
 *
 * It holds its id, whose it is, how they proved it and when, what kind of client and device it is on, and
 * when it was last used, and answers one question about them: whether it may still be used at a moment
 * ([standingAt]). It does not know its secret, which only its keeper does. Who holds it cannot change: a
 * session is replaced, never edited, so a different person or a different sign-in means a new secret.
 * What does change is what is said about it ([renamed]), when it was last used ([seenAt]) and when its
 * person last proved who they are again ([confirmed], ADR-092). That last is not a new sign-in: the
 * absolute lifetime still counts from [authenticatedAt], so confirming never makes a session live longer.
 *
 * Not a `data class`, for the reason `Attempt` is not one: a generated `copy()` is public.
 *
 * Its state leaves through [writeTo] and comes back through [restore] (ADR-085), and through nothing else.
 */
public class Session private constructor(
    private val id: SessionId,
    private val account: Uuid,
    private val factors: Set<Factor>,
    private val client: ClientType,
    private val device: Device,
    private val authenticatedAt: Instant,
    private val confirmedAt: Instant,
    private val lastActiveAt: Instant,
) {
    init {
        require(factors.isNotEmpty()) { "A session records how its person proved who they are; none is no session." }
    }

    /**
     * Whether this session may be used at [now] under [rules], or why it may not. It takes the lifetimes
     * of its own kind of client.
     *
     * Age is asked first: a session that is past its absolute lifetime is over however recently it was used.
     */
    public fun standingAt(now: Instant, rules: LifetimeRules): Standing {
        val lifetimes = rules.of(client)
        return when {
            lifetimes.hasPassedSinceStart(authenticatedAt, now) -> Standing.EndedByAge()
            lifetimes.hasPassedSinceUse(lastActiveAt, now) -> Standing.EndedByIdleness()
            else -> Standing.Live(id, account, factors, authenticatedAt, freshnessAt(now, lifetimes))
        }
    }

    private fun freshnessAt(now: Instant, lifetimes: Lifetimes): Freshness =
        if (lifetimes.hasLostFreshness(confirmedAt, now)) Freshness.Stale else Freshness.Fresh

    /**
     * This session, used at [now]. Use only moves forward: a [now] before the last use leaves it as it was.
     */
    public fun seenAt(now: Instant): Session =
        Session(id, account, factors, client, device, authenticatedAt, confirmedAt, maxOf(lastActiveAt, now))

    /**
     * This session, its person having proved who they are again by [proved] at [at]: the factors join
     * those it already records, and the moment of the last proof moves forward. Never back: a proof
     * stamped before the last one leaves it as it was, so a slow request cannot make a session staler.
     */
    public fun confirmed(at: Instant, proved: Set<Factor>): Session =
        Session(id, account, factors + proved, client, device, authenticatedAt, maxOf(confirmedAt, at), lastActiveAt)

    /**
     * This session, with its device called [name].
     */
    public fun renamed(name: DeviceName): Session =
        Session(id, account, factors, client, device.named(name), authenticatedAt, confirmedAt, lastActiveAt)

    /**
     * Whether this session is good on [surface]: the door its kind of client uses, and no other (ADR-097).
     */
    public fun isHeldOn(surface: Surface): Boolean = client.isOn(surface)

    /**
     * Whether this is [other], for whoever keeps sessions and has to find one.
     */
    public fun isIdentifiedBy(other: SessionId): Boolean = id == other

    /**
     * Whether this session is [person]'s.
     */
    public fun isOf(person: Uuid): Boolean = account == person

    /**
     * Tells [record] everything about this session.
     */
    public fun writeTo(record: Record) {
        record.session(id, account, client, authenticatedAt, confirmedAt, lastActiveAt)
        device.writeTo(record)
        Factor.entries.filter { it in factors }.forEach(record::proved)
    }

    override fun toString(): String = "Session(authenticatedAt=$authenticatedAt)"

    /**
     * Whoever keeps a session, told what it holds.
     */
    public interface Record : Device.Record {
        public fun session(
            id: SessionId,
            account: Uuid,
            client: ClientType,
            authenticatedAt: Instant,
            confirmedAt: Instant,
            lastActiveAt: Instant,
        )

        public fun proved(factor: Factor)
    }

    public companion object {
        /**
         * How coarsely last use is kept. A person who clicks around does not make the database write on
         * every request, and a day-long idle limit does not notice a minute.
         */
        public val USE_GRAIN: Duration = 1.minutes

        /**
         * A session known as [id] for [account] who proved who they are by [factors], the last of them at
         * [authenticatedAt], begun at [now] by a [client] on [device]. That proof is also the last
         * confirmation: a person who has just signed in may do a dangerous act without being asked again.
         *
         * A [now] before [authenticatedAt], which two clocks that disagree by milliseconds can make, is
         * taken as [authenticatedAt] (slice 3, fork 5).
         */
        public fun begin(
            id: SessionId,
            account: Uuid,
            factors: Set<Factor>,
            client: ClientType,
            device: Device,
            authenticatedAt: Instant,
            now: Instant,
        ): Session = Session(
            id,
            account,
            factors.toSet(),
            client,
            device,
            authenticatedAt,
            authenticatedAt,
            maxOf(now, authenticatedAt),
        )

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
        private val told = mutableListOf<Told>()
        private val devices = mutableListOf<Device>()
        private val factors = mutableSetOf<Factor>()

        override fun session(
            id: SessionId,
            account: Uuid,
            client: ClientType,
            authenticatedAt: Instant,
            confirmedAt: Instant,
            lastActiveAt: Instant,
        ) {
            told += Told(id, account, client, authenticatedAt, confirmedAt, lastActiveAt)
        }

        override fun device(browser: Browser, platform: Platform, mobile: Boolean, name: String?) {
            devices += Device.restore(browser, platform, mobile, name)
        }

        override fun proved(factor: Factor) {
            check(factors.add(factor)) { "A session was kept proved twice by $factor; writeTo tells each once." }
        }

        fun session(): Session {
            check(told.size == 1) { "Storage replayed ${told.size} sessions where one was kept." }
            check(devices.size == 1) { "Storage replayed ${devices.size} devices where one was kept." }
            val one = told.single()
            check(factors.isNotEmpty()) {
                "A session was kept with no proof of who its person is; writeTo never tells that."
            }
            check(one.lastActiveAt >= one.authenticatedAt) {
                "A session kept as last used (${one.lastActiveAt}) before it began (${one.authenticatedAt}); " +
                    "writeTo never tells that."
            }
            check(one.confirmedAt >= one.authenticatedAt) {
                "A session kept as last confirmed (${one.confirmedAt}) before it began (${one.authenticatedAt}); " +
                    "writeTo never tells that."
            }
            return Session(
                one.id,
                one.account,
                factors.toSet(),
                one.client,
                devices.single(),
                one.authenticatedAt,
                one.confirmedAt,
                one.lastActiveAt,
            )
        }
    }

    private class Told(
        val id: SessionId,
        val account: Uuid,
        val client: ClientType,
        val authenticatedAt: Instant,
        val confirmedAt: Instant,
        val lastActiveAt: Instant,
    )
}
