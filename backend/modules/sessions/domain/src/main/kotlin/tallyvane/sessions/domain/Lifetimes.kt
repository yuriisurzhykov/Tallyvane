package tallyvane.sessions.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * How long a session may live: for how long it may go unused, and for how long it may live at all
 * (ADR-079).
 *
 * Applied to every request, not stored as a date on the session, so tightening them reaches the
 * sessions that already exist at their next request.
 *
 * The code sets the bounds and the policy sets the values (ADR-078): an idle limit of 15 minutes to 30
 * days and an absolute limit of at most 90 days, never less than the idle limit. A value outside them
 * cannot be built, so an administrator's mistake, or a captured admin API, cannot make a session live
 * ten years.
 */
public class Lifetimes internal constructor(private val idle: Duration, private val absolute: Duration) {
    init {
        require(idle in IDLE) { "A session may sit idle for $IDLE, not $idle." }
        require(absolute <= LONGEST) { "A session may live at most $LONGEST, not $absolute." }
        require(absolute >= idle) {
            "A session may live at most $absolute, which is less than it may sit idle ($idle): " +
                "the idle limit could never be reached."
        }
    }

    internal fun hasPassedSinceStart(authenticatedAt: Instant, now: Instant): Boolean =
        now - authenticatedAt >= absolute

    internal fun hasPassedSinceUse(lastActiveAt: Instant, now: Instant): Boolean = now - lastActiveAt >= idle

    override fun toString(): String = "Lifetimes(idle=$idle, absolute=$absolute)"

    public companion object {
        private val IDLE: ClosedRange<Duration> = 15.minutes..30.days

        /**
         * The longest any session can be allowed to live, which is as long as its cookie is told to be
         * remembered. The browser is told the bound and not today's value, so a policy loosened later
         * reaches the cookies already issued; the server is what decides when a session is over.
         */
        public val LONGEST: Duration = 90.days
    }
}
