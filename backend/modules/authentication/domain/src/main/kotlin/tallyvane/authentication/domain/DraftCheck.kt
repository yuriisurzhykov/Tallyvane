package tallyvane.authentication.domain

/**
 * What [PolicyDraft.check] found: a policy that may exist, or every reason it may not.
 *
 * All the reasons at once, not the first one: an administrator fixing a form should not discover
 * the second mistake only after fixing the first.
 */
public sealed interface DraftCheck {
    /**
     * Tells [report] which outcome this is, with what it carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * The draft is within every bound; [policy] is the only way a [SignInPolicy] comes to exist.
     */
    @ConsistentCopyVisibility
    public data class Passed internal constructor(private val policy: SignInPolicy) : DraftCheck {
        override fun <T> reportTo(report: Report<T>): T = report.passed(policy)
    }

    /**
     * The draft breaks at least one bound; [violations] lists every one, never empty.
     */
    @ConsistentCopyVisibility
    public data class Rejected internal constructor(private val violations: List<Violation>) : DraftCheck {
        override fun <T> reportTo(report: Report<T>): T = report.rejected(violations)
    }

    /**
     * A reader of [DraftCheck], one method per outcome.
     */
    public interface Report<out T> {
        public fun passed(policy: SignInPolicy): T

        public fun rejected(violations: List<Violation>): T
    }
}
