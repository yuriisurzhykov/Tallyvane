package tallyvane.sessions.domain

import kotlin.time.Duration

/**
 * One reason a [LifetimeDraft] cannot become lifetimes (ADR-078: the code sets bounds, the policy sets values).
 *
 * Each case says what was given and what is allowed, so the admin screen can explain the refusal without knowing the
 * bounds itself. Like `Violation` in `authentication`, it is read only through [reportTo], and only [LifetimeDraft]
 * makes one.
 */
public sealed interface LifetimeViolation {
    /**
     * Tells [report] which violation this is, with what it carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * A session may sit unused for too short a time to be of use, or long enough to be worth stealing.
     */
    @ConsistentCopyVisibility
    public data class IdleOutOfBounds internal constructor(
        private val given: Duration,
        private val allowed: ClosedRange<Duration>,
    ) : LifetimeViolation {
        override fun <T> reportTo(report: Report<T>): T = report.idleOutOfBounds(given, allowed)
    }

    /**
     * A session may live no longer than its cookie is remembered for.
     */
    @ConsistentCopyVisibility
    public data class AbsoluteOutOfBounds internal constructor(
        private val given: Duration,
        private val longest: Duration,
    ) : LifetimeViolation {
        override fun <T> reportTo(report: Report<T>): T = report.absoluteOutOfBounds(given, longest)
    }

    /**
     * A session would be over, by age, before it could be over by being unused: the idle limit could never be
     * reached.
     */
    @ConsistentCopyVisibility
    public data class AbsoluteBelowIdle internal constructor(
        private val absolute: Duration,
        private val idle: Duration,
    ) : LifetimeViolation {
        override fun <T> reportTo(report: Report<T>): T = report.absoluteBelowIdle(absolute, idle)
    }

    /**
     * A proof of who the person is may stay fresh for too short a time to do anything with, or for so long that a
     * dangerous act is not confirmed in earnest (ADR-092).
     */
    @ConsistentCopyVisibility
    public data class FreshnessOutOfBounds internal constructor(
        private val given: Duration,
        private val allowed: ClosedRange<Duration>,
    ) : LifetimeViolation {
        override fun <T> reportTo(report: Report<T>): T = report.freshnessOutOfBounds(given, allowed)
    }

    /**
     * A reader of [LifetimeViolation], one method per case.
     */
    public interface Report<out T> {
        public fun idleOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): T

        public fun absoluteOutOfBounds(given: Duration, longest: Duration): T

        public fun absoluteBelowIdle(absolute: Duration, idle: Duration): T

        public fun freshnessOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): T
    }
}
