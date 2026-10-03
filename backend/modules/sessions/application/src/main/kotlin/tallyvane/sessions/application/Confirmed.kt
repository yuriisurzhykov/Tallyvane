package tallyvane.sessions.application

import tallyvane.platform.kernel.Failure

/**
 * How confirming a dangerous act ended (ADR-092).
 */
public sealed interface Confirmed {
    /**
     * The person proved who they are again, and their session now counts it. The session itself, its
     * secret and its lifetime are what they were.
     */
    public class Done : Confirmed {
        override fun equals(other: Any?): Boolean = other is Done

        override fun hashCode(): Int = Done::class.hashCode()

        override fun toString(): String = "Done"
    }

    public sealed interface Failed :
        Confirmed,
        Failure {
        /**
         * No session was presented.
         */
        public class SignInRequired : Failed {
            override fun equals(other: Any?): Boolean = other is SignInRequired

            override fun hashCode(): Int = SignInRequired::class.hashCode()

            override fun toString(): String = "SignInRequired"
        }

        /**
         * The session presented is over, or nobody issued it.
         */
        public class SessionExpired : Failed {
            override fun equals(other: Any?): Boolean = other is SessionExpired

            override fun hashCode(): Int = SessionExpired::class.hashCode()

            override fun toString(): String = "SessionExpired"
        }

        /**
         * There is no finished confirmation to take: no cookie, one that expired, one already used, or one
         * that is not finished (a code still to come).
         */
        public class NothingToConfirm : Failed {
            override fun equals(other: Any?): Boolean = other is NothingToConfirm

            override fun hashCode(): Int = NothingToConfirm::class.hashCode()

            override fun toString(): String = "NothingToConfirm"
        }

        /**
         * The confirmation is someone else's: the person who proved who they are is not the one signed in.
         * The confirmation is spent and the session is unchanged.
         */
        public class WrongAccount : Failed {
            override fun equals(other: Any?): Boolean = other is WrongAccount

            override fun hashCode(): Int = WrongAccount::class.hashCode()

            override fun toString(): String = "WrongAccount"
        }
    }
}
