package tallyvane.authentication.application

import tallyvane.platform.kernel.Secret

/**
 * Where a person goes after coming back from Google: onward with their sign-in, to the welcome screen
 * as somebody new, or back to the sign-in page.
 *
 * Not a [tallyvane.platform.kernel.Failure] even when it turns back: the person arrives by a redirect
 * from Google, so every case ends in another redirect, never in an error document.
 */
public sealed interface GoogleReturn {
    public fun <T> reportTo(report: Report<T>): T

    /**
     * Google identified somebody who has an account, and the attempt now says so. What the sign-in
     * still needs, and the session that ends it, are the next step's business (slice 3b).
     */
    public class Verified internal constructor() : GoogleReturn {
        override fun <T> reportTo(report: Report<T>): T = report.verified()

        override fun equals(other: Any?): Boolean = other is Verified

        override fun hashCode(): Int = Verified::class.hashCode()

        override fun toString(): String = "Verified"
    }

    /**
     * Google identified somebody on a trip that began as a confirmation of a dangerous act (ADR-092), and
     * the attempt now says so. Whether it is the person who asked is for the module that holds the session
     * to say; this module cannot.
     */
    public class SteppedUp internal constructor() : GoogleReturn {
        override fun <T> reportTo(report: Report<T>): T = report.steppedUp()

        override fun equals(other: Any?): Boolean = other is SteppedUp

        override fun hashCode(): Int = SteppedUp::class.hashCode()

        override fun toString(): String = "SteppedUp"
    }

    /**
     * Google identified somebody without an account. Their sign-in became a registration under a new
     * secret, the [attempt] the browser holds from now on (slice 3, fork 3).
     */
    public class Registering internal constructor(private val attempt: Secret) : GoogleReturn {
        override fun <T> reportTo(report: Report<T>): T = report.registering(attempt)

        override fun toString(): String = "Registering(***)"
    }

    /**
     * The sign-in cannot go on, for [reason]; the attempt is forgotten.
     */
    public class TurnedBack internal constructor(private val reason: TurnBack) : GoogleReturn {
        override fun <T> reportTo(report: Report<T>): T = report.turnedBack(reason)

        override fun equals(other: Any?): Boolean = other is TurnedBack && other.reason == reason

        override fun hashCode(): Int = reason.hashCode()

        override fun toString(): String = "TurnedBack($reason)"
    }

    /**
     * A reader of [GoogleReturn], one method per case.
     */
    public interface Report<out T> {
        public fun verified(): T

        public fun steppedUp(): T

        public fun registering(attempt: Secret): T

        public fun turnedBack(reason: TurnBack): T
    }
}
