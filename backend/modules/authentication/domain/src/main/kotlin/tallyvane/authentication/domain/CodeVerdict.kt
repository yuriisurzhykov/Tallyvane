package tallyvane.authentication.domain

/**
 * What a TOTP enrolment made of a code a person typed.
 *
 * Closed on purpose: the code was right and the enrolment moved on, or it was not. A code that is
 * right but was already used is not right.
 */
public sealed interface CodeVerdict {
    /**
     * Tells [report] which case this is, with what that case carries.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * The code was right and its time step had not been used. [next] is the enrolment that remembers it.
     */
    public class Accepted internal constructor(private val next: TotpEnrollment) : CodeVerdict {
        override fun <T> reportTo(report: Report<T>): T = report.accepted(next)

        override fun toString(): String = "Accepted"
    }

    /**
     * The code was not right, was for a step already used, or the enrolment is not in a standing to
     * check one.
     */
    public class Wrong internal constructor() : CodeVerdict {
        override fun <T> reportTo(report: Report<T>): T = report.wrong()

        override fun equals(other: Any?): Boolean = other is Wrong

        override fun hashCode(): Int = Wrong::class.hashCode()

        override fun toString(): String = "Wrong"
    }

    /**
     * A reader of [CodeVerdict], one method per case.
     */
    public interface Report<out T> {
        public fun accepted(next: TotpEnrollment): T

        public fun wrong(): T
    }
}
