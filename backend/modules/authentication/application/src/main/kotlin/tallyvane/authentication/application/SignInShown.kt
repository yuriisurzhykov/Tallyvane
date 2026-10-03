package tallyvane.authentication.application

import tallyvane.authentication.domain.Progress
import tallyvane.platform.kernel.Failure

/**
 * What asking where a sign-in or a confirmation stands came to.
 */
public sealed interface SignInShown {
    /**
     * The attempt stands [progress]: waiting for a factor, paused, complete, and so on.
     */
    public class Shown internal constructor(private val progress: Progress) : SignInShown {
        /**
         * Tells [report] where the attempt stands.
         */
        public fun <T> reportTo(report: Progress.Report<T>): T = progress.reportTo(report)

        override fun toString(): String = "Shown($progress)"
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
