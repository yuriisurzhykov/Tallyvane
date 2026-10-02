package tallyvane.sessions.application

import tallyvane.platform.kernel.Failure
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration

/**
 * How asking for a session ended.
 */
public sealed interface Opened {
    /**
     * A session was issued. Its secret goes to the browser once and is held nowhere else.
     */
    public class Issued internal constructor(private val secret: Secret, private val lasting: Duration) : Opened {
        /**
         * Tells [record] the secret and how long the session can last at most, which is as long as the
         * browser needs to remember it.
         */
        public fun writeTo(record: Record) {
            record.session(secret, lasting)
        }

        override fun toString(): String = "Issued(***)"

        /**
         * Whoever gives the browser its session.
         */
        public fun interface Record {
            public fun session(secret: Secret, lasting: Duration)
        }
    }

    public sealed interface Failed :
        Opened,
        Failure {
        /**
         * There is no completed sign-in to exchange: no cookie, one that expired, one already used, or
         * one that is not finished (a registration waiting for its form, a factor still to come).
         */
        public class NothingToOpen : Failed {
            override fun equals(other: Any?): Boolean = other is NothingToOpen

            override fun hashCode(): Int = NothingToOpen::class.hashCode()

            override fun toString(): String = "NothingToOpen"
        }
    }
}
