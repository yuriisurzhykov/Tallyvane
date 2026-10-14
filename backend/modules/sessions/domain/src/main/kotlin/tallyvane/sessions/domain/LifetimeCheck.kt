package tallyvane.sessions.domain

/**
 * What [LifetimeDraft.check] found: lifetimes that may exist, or every reason they may not.
 *
 * All the reasons at once, not the first one: an administrator fixing a form should not discover the second mistake
 * only after fixing the first.
 */
public sealed interface LifetimeCheck {
    /**
     * Tells [report] which outcome this is, with what it carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * The draft is within every bound.
     */
    @ConsistentCopyVisibility
    public data class Passed internal constructor(private val lifetimes: ClientLifetimes) : LifetimeCheck {
        override fun <T> reportTo(report: Report<T>): T = report.passed(lifetimes)
    }

    /**
     * The draft breaks at least one bound; the violations are never none.
     */
    @ConsistentCopyVisibility
    public data class Rejected internal constructor(private val violations: List<LifetimeViolation>) : LifetimeCheck {
        override fun <T> reportTo(report: Report<T>): T = report.rejected(violations)
    }

    /**
     * A reader of [LifetimeCheck], one method per outcome.
     */
    public interface Report<out T> {
        public fun passed(lifetimes: ClientLifetimes): T

        public fun rejected(violations: List<LifetimeViolation>): T
    }
}
