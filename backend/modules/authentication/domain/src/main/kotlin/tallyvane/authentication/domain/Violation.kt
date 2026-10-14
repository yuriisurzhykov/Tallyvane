package tallyvane.authentication.domain

import kotlin.time.Duration

/**
 * One reason a [PolicyDraft] cannot become a policy (ADR-078: the code sets bounds, the policy
 * sets values).
 *
 * Each case says what was given and what is allowed, so the admin screen can explain the refusal
 * without knowing the bounds itself. Like [Progress], a violation is read only through [reportTo],
 * and only [PolicyDraft] makes one.
 */
public sealed interface Violation {
    /**
     * Tells [report] which violation this is, with what it carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * An attempt would stay open too briefly to finish, or long enough to be worth stealing.
     */
    @ConsistentCopyVisibility
    public data class AttemptLifetimeOutOfBounds internal constructor(
        private val given: Duration,
        private val allowed: ClosedRange<Duration>,
    ) : Violation {
        override fun <T> reportTo(report: Report<T>): T = report.attemptLifetimeOutOfBounds(given, allowed)
    }

    /**
     * Too few wrong answers would end honest typos; too many would leave room to guess.
     */
    @ConsistentCopyVisibility
    public data class MaxFailuresOutOfBounds internal constructor(
        private val given: Int,
        private val allowed: IntRange,
    ) : Violation {
        override fun <T> reportTo(report: Report<T>): T = report.maxFailuresOutOfBounds(given, allowed)
    }

    /**
     * The first pause after a wrong answer, which every later one doubles (ADR-082).
     */
    @ConsistentCopyVisibility
    public data class FirstDelayOutOfBounds internal constructor(
        private val given: Duration,
        private val allowed: ClosedRange<Duration>,
    ) : Violation {
        override fun <T> reportTo(report: Report<T>): T = report.firstDelayOutOfBounds(given, allowed)
    }

    /**
     * The step at [position], counted from one, accepts no factor at all.
     */
    @ConsistentCopyVisibility
    public data class EmptyStep internal constructor(private val position: Int) : Violation {
        override fun <T> reportTo(report: Report<T>): T = report.emptyStep(position)
    }

    /**
     * No step can be passed by every account without setup, so nothing would tell us whose account
     * is signing in.
     */
    public class NothingIdentifiesTheAccount internal constructor() : Violation {
        override fun <T> reportTo(report: Report<T>): T = report.nothingIdentifiesTheAccount()

        override fun equals(other: Any?): Boolean = other is NothingIdentifiesTheAccount

        override fun hashCode(): Int = NothingIdentifiesTheAccount::class.hashCode()

        override fun toString(): String = "NothingIdentifiesTheAccount"
    }

    /**
     * The purpose demands a second factor from everyone, and no step does.
     */
    @ConsistentCopyVisibility
    public data class BelowTheFloor internal constructor(private val purpose: Purpose) : Violation {
        override fun <T> reportTo(report: Report<T>): T = report.belowTheFloor(purpose)
    }

    /**
     * The policy demands a second factor from everyone, and an account that has not set one up could not sign in:
     * there is no session that lets a person in only to set it up (ADR-097, ADR-098). Only the administrators'
     * sign-in may demand it, because an administrator sets TOTP up on the console before being given the right.
     */
    @ConsistentCopyVisibility
    public data class WouldLockPeopleOut internal constructor(private val purpose: Purpose) : Violation {
        override fun <T> reportTo(report: Report<T>): T = report.wouldLockPeopleOut(purpose)
    }

    /**
     * A reader of [Violation], one method per case.
     */
    public interface Report<out T> {
        public fun attemptLifetimeOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): T

        public fun maxFailuresOutOfBounds(given: Int, allowed: IntRange): T

        public fun firstDelayOutOfBounds(given: Duration, allowed: ClosedRange<Duration>): T

        public fun emptyStep(position: Int): T

        public fun nothingIdentifiesTheAccount(): T

        public fun belowTheFloor(purpose: Purpose): T

        public fun wouldLockPeopleOut(purpose: Purpose): T
    }
}
