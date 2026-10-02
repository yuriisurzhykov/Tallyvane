package tallyvane.sessions.application

import tallyvane.identity.contract.AccountId

/**
 * Who a browser's session says it is.
 *
 * Their contents are private, and the only way to read them is [reportTo].
 */
public sealed interface Resolution {
    /**
     * Tells [report] which case this is, with what that case carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * What a [Resolution] says about itself, one method per case.
     */
    public interface Report<out T> {
        public fun signedIn(account: AccountId): T

        public fun lapsed(): T

        public fun anonymous(): T
    }

    /**
     * The session is good, and it is this person's.
     */
    public class SignedIn internal constructor(private val account: AccountId) : Resolution {
        override fun <T> reportTo(report: Report<T>): T = report.signedIn(account)

        override fun equals(other: Any?): Boolean = other is SignedIn && other.account == account

        override fun hashCode(): Int = account.hashCode()

        override fun toString(): String = "SignedIn"
    }

    /**
     * A session was presented, and it is not good: it ended, or nobody issued it.
     */
    public class Lapsed internal constructor() : Resolution {
        override fun <T> reportTo(report: Report<T>): T = report.lapsed()

        override fun equals(other: Any?): Boolean = other is Lapsed

        override fun hashCode(): Int = Lapsed::class.hashCode()

        override fun toString(): String = "Lapsed"
    }

    /**
     * No session was presented.
     */
    public class Anonymous internal constructor() : Resolution {
        override fun <T> reportTo(report: Report<T>): T = report.anonymous()

        override fun equals(other: Any?): Boolean = other is Anonymous

        override fun hashCode(): Int = Anonymous::class.hashCode()

        override fun toString(): String = "Anonymous"
    }
}
