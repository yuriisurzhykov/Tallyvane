package tallyvane.authentication.application

import tallyvane.authentication.domain.FactorKind
import tallyvane.authentication.domain.Progress
import tallyvane.platform.kernel.Failure
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What asking where a sign-in or a confirmation stands came to.
 */
public sealed interface SignInShown {
    /**
     * The attempt stands [progress]: waiting for a factor, paused, complete, and so on.
     */
    public class Shown internal constructor(private val progress: Progress, private val now: Instant) : SignInShown {
        /**
         * Tells [report] where the attempt stands, with everything the domain knows about it.
         */
        public fun <T> reportTo(report: Progress.Report<T>): T = progress.reportTo(report)

        /**
         * Tells [report] where the attempt stands, as a page needs it: which answers are wanted, or how
         * long to wait.
         */
        public fun <T> reportTo(report: Report<T>): T = progress.reportTo(Telling(report, now))

        override fun toString(): String = "Shown($progress)"

        private class Telling<T>(private val report: Report<T>, private val now: Instant) : Progress.Report<T> {
            override fun complete(factors: Set<FactorKind>, authenticatedAt: Instant, subject: String): T =
                report.complete()

            override fun restricted(
                factors: Set<FactorKind>,
                authenticatedAt: Instant,
                subject: String,
                toSetUp: Set<FactorKind>,
            ): T = report.restricted()

            override fun awaiting(accepted: Set<FactorKind>): T =
                report.awaiting(FactorKind.Totp in accepted, FactorKind.RecoveryCode in accepted)

            override fun paused(accepted: Set<FactorKind>, until: Instant): T = report.paused(
                FactorKind.Totp in accepted,
                FactorKind.RecoveryCode in accepted,
                until - now,
            )

            override fun exhausted(): T = report.exhausted()

            override fun expired(): T = report.expired()
        }

        /**
         * A reader of [Shown], one method per place an attempt can stand.
         */
        public interface Report<out T> {
            /**
             * The attempt waits for an answer: a code from the authenticator when [totp], a recovery code
             * when [recoveryCode].
             */
            public fun awaiting(totp: Boolean, recoveryCode: Boolean): T

            /**
             * As [awaiting], but a pause is running: the next answer is not looked at for [wait].
             */
            public fun paused(totp: Boolean, recoveryCode: Boolean, wait: Duration): T

            /**
             * Everything wanted is proved. The sign-in is redeemed for a session, a confirmation is taken.
             */
            public fun complete(): T

            /**
             * Everything wanted is proved, but the person must still set up a factor first.
             */
            public fun restricted(): T

            /**
             * The attempt took its last wrong answer and is over.
             */
            public fun exhausted(): T

            /**
             * The attempt outlived its lifetime.
             */
            public fun expired(): T
        }
    }

    public sealed interface Failed :
        SignInShown,
        Failure {
        /**
         * There is no sign-in or confirmation under what the browser sent: none was begun, or it is gone.
         */
        public class NoSignIn internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NoSignIn

            override fun hashCode(): Int = NoSignIn::class.hashCode()

            override fun toString(): String = "NoSignIn"
        }
    }
}
