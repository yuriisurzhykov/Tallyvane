package tallyvane.authentication.application

/**
 * What came of trading a code with Google: an identity it vouches for, or why there is none.
 *
 * Read through [reportTo], so a reader handles every case or does not compile.
 */
public sealed interface GoogleAnswer {
    public fun <T> reportTo(report: Report<T>): T

    /**
     * Google vouches that this person is [subject] in its own records, with a verified address.
     */
    public class Vouched(private val subject: String, private val profile: GoogleProfile) : GoogleAnswer {
        init {
            require(subject.isNotBlank()) { "Google vouched for nobody: the subject is blank." }
        }

        override fun <T> reportTo(report: Report<T>): T = report.vouched(subject, profile)

        override fun toString(): String = "Vouched(***)"
    }

    /**
     * The person is known to Google, but Google has not verified their address. An address nobody
     * proved is not one this system will write to.
     */
    public class EmailUnverified : GoogleAnswer {
        override fun <T> reportTo(report: Report<T>): T = report.emailUnverified()

        override fun equals(other: Any?): Boolean = other is EmailUnverified

        override fun hashCode(): Int = EmailUnverified::class.hashCode()

        override fun toString(): String = "EmailUnverified"
    }

    /**
     * Google did not accept the code, or what it sent back did not hold up: a signature that does not
     * verify, a token for another client or another trip. Which of these it was is in the log, not in
     * the answer, because the person can do the same about all of them: start again.
     */
    public class Refused : GoogleAnswer {
        override fun <T> reportTo(report: Report<T>): T = report.refused()

        override fun equals(other: Any?): Boolean = other is Refused

        override fun hashCode(): Int = Refused::class.hashCode()

        override fun toString(): String = "Refused"
    }

    /**
     * Google could not be asked: the network, or Google itself, failed. Trying again later may work.
     */
    public class Unreachable : GoogleAnswer {
        override fun <T> reportTo(report: Report<T>): T = report.unreachable()

        override fun equals(other: Any?): Boolean = other is Unreachable

        override fun hashCode(): Int = Unreachable::class.hashCode()

        override fun toString(): String = "Unreachable"
    }

    /**
     * A reader of [GoogleAnswer], one method per case.
     */
    public interface Report<out T> {
        public fun vouched(subject: String, profile: GoogleProfile): T

        public fun emailUnverified(): T

        public fun refused(): T

        public fun unreachable(): T
    }
}
