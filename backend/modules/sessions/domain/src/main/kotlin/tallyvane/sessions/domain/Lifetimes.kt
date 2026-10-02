package tallyvane.sessions.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * How long a session may live: for how long it may go unused, and for how long it may live at all
 * (ADR-079).
 *
 * Applied to every request, not stored as a date on the session, so tightening them reaches the
 * sessions that already exist at their next request.
 */
public class Lifetimes(private val idle: Duration, private val absolute: Duration) {
    init {
        require(idle.isPositive()) { "A session that may be idle for no time at all ends before it is used." }
        require(absolute >= idle) {
            "A session may live at most $absolute, which is less than it may sit idle ($idle): " +
                "the idle limit could never be reached."
        }
    }

    /**
     * The longest a session can live, which is as long as its cookie has to be remembered.
     */
    public fun longest(): Duration = absolute

    internal fun hasPassedSinceStart(authenticatedAt: Instant, now: Instant): Boolean =
        now - authenticatedAt >= absolute

    internal fun hasPassedSinceUse(lastActiveAt: Instant, now: Instant): Boolean = now - lastActiveAt >= idle

    override fun toString(): String = "Lifetimes(idle=$idle, absolute=$absolute)"

    public companion object {
        /**
         * A browser's: a day unused, a week at most. Fixed in code until slice 4 reads them from the
         * policy (ADR-079).
         */
        public fun forBrowser(): Lifetimes = Lifetimes(idle = 1.days, absolute = 7.days)
    }
}
