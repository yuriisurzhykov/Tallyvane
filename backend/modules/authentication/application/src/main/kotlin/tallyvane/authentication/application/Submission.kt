package tallyvane.authentication.application

/**
 * What a person typed to answer a second step: a code from their authenticator, or a recovery code.
 *
 * What it holds is private, and the way to read it is [reportTo], so a reader must say what it does for
 * each case.
 */
public sealed interface Submission {
    /**
     * Tells [report] which case this is, with the text typed.
     */
    public fun <T> reportTo(report: Report<T>): T

    /**
     * A code from the person's authenticator app.
     */
    public class TotpCode(private val code: String) : Submission {
        override fun <T> reportTo(report: Report<T>): T = report.totp(code)

        override fun toString(): String = "TotpCode"
    }

    /**
     * A recovery code, written down when TOTP was enabled.
     */
    public class RecoveryCode(private val code: String) : Submission {
        override fun <T> reportTo(report: Report<T>): T = report.recovery(code)

        override fun toString(): String = "RecoveryCode"
    }

    /**
     * A reader of [Submission], one method per case.
     */
    public interface Report<out T> {
        public fun totp(code: String): T

        public fun recovery(code: String): T
    }
}
