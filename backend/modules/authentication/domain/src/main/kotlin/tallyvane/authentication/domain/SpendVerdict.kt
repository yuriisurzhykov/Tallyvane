package tallyvane.authentication.domain

/**
 * What a set of recovery codes made of a code a person typed.
 */
public sealed interface SpendVerdict {
    /**
     * Tells [report] which case this is, with what that case carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * The code was one of the unspent ones and is now spent. [next] is the set without it.
     */
    public class Spent internal constructor(private val next: RecoveryCodes) : SpendVerdict {
        override fun <T> reportTo(report: Report<T>): T = report.spent(next)

        override fun toString(): String = "Spent"
    }

    /**
     * No unspent code matches: never issued, or already spent.
     */
    public class Unknown internal constructor() : SpendVerdict {
        override fun <T> reportTo(report: Report<T>): T = report.unknown()

        override fun equals(other: Any?): Boolean = other is Unknown

        override fun hashCode(): Int = Unknown::class.hashCode()

        override fun toString(): String = "Unknown"
    }

    /**
     * A reader of [SpendVerdict], one method per case.
     */
    public interface Report<out T> {
        public fun spent(next: RecoveryCodes): T

        public fun unknown(): T
    }
}
