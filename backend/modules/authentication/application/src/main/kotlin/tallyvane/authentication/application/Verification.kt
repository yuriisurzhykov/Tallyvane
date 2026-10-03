package tallyvane.authentication.application

import tallyvane.platform.kernel.Failure
import kotlin.time.Duration

/**
 * How answering a second step ended.
 */
public sealed interface Verification {
    /**
     * The answer was right, and the attempt records the factor it proved. What the sign-in or the
     * confirmation still needs is the next request's business: it is asked where it stands.
     */
    public class Verified internal constructor(private val recoveryCodesLeft: Int?) : Verification {
        /**
         * Tells [report] whether a code from the authenticator was accepted, or a recovery code, which
         * leaves this many unspent.
         */
        public fun <T> reportTo(report: Report<T>): T = recoveryCodesLeft?.let(report::recovery) ?: report.totp()

        override fun toString(): String = "Verified"

        /**
         * A reader of [Verified], one method per kind of answer.
         */
        public interface Report<out T> {
            public fun totp(): T

            public fun recovery(remaining: Int): T
        }
    }

    /**
     * The answer was not taken.
     */
    public sealed interface Failed :
        Verification,
        Failure {
        /**
         * Tells [told] how long the person has to wait before trying again, when they do. Most failures
         * have no such wait and tell nothing.
         */
        public fun retryAfter(told: (Duration) -> Unit) {}

        /**
         * The answer was wrong. [wait] is the pause that now applies before the next one, when there is
         * one.
         */
        public class WrongCode internal constructor(private val wait: Duration?) : Failed {
            override fun retryAfter(told: (Duration) -> Unit) {
                wait?.let(told)
            }

            override fun equals(other: Any?): Boolean = other is WrongCode && other.wait == wait

            override fun hashCode(): Int = wait.hashCode()

            override fun toString(): String = "WrongCode($wait)"
        }

        /**
         * A pause is still running, the attempt's or the account's, and the answer was not even checked.
         */
        public class Paused internal constructor(private val wait: Duration) : Failed {
            override fun retryAfter(told: (Duration) -> Unit) {
                told(wait)
            }

            override fun equals(other: Any?): Boolean = other is Paused && other.wait == wait

            override fun hashCode(): Int = wait.hashCode()

            override fun toString(): String = "Paused($wait)"
        }

        /**
         * There is no sign-in or confirmation to answer: none was begun, it is over, expired, or ended by
         * its last wrong answer. The person starts again.
         */
        public class Closed internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is Closed

            override fun hashCode(): Int = Closed::class.hashCode()

            override fun toString(): String = "Closed"
        }

        /**
         * The sign-in is not waiting for this kind of answer: Google has not been answered yet, or nothing
         * more is wanted.
         */
        public class NotWanted internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is NotWanted

            override fun hashCode(): Int = NotWanted::class.hashCode()

            override fun toString(): String = "NotWanted"
        }

        /**
         * Another request changed the same sign-in at the same moment. Nothing was taken from this one; the
         * person asks again.
         */
        public class Busy internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is Busy

            override fun hashCode(): Int = Busy::class.hashCode()

            override fun toString(): String = "Busy"
        }
    }
}
