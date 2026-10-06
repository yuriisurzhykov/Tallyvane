package tallyvane.authentication.domain

/**
 * What a person has to say about their TOTP when they ask where it stands: off, working, or retired
 * (ADR-093).
 *
 * It is read from the enrolment and the recovery codes together, because the enrolment alone cannot
 * tell a seed that was never confirmed from one that replaced a confirmed seed. A set of recovery codes
 * is made only when a first code is confirmed and goes only with the enrolment it belongs to, so a
 * `Pending` enrolment that has a set kept beside it was begun again over a seed that worked once. That
 * person has recovery codes and no working app, which is what [Report.retired] says, and not "off".
 *
 * @param totp The account's enrolment, or null when it has none.
 * @param codes The account's recovery codes, or null when it was never given any.
 */
public class TotpStanding(private val totp: TotpEnrollment?, private val codes: RecoveryCodes?) {
    /**
     * Tells [report] which case this is, with the recovery codes still unspent where that matters.
     */
    public fun <T> reportTo(report: Report<T>): T {
        val left = codes?.remaining() ?: 0
        return when {
            totp == null -> report.off()
            totp.isActive() -> report.active(left)
            totp.isRetired() -> report.retired(left)
            codes != null -> report.retired(left)
            else -> report.off()
        }
    }

    override fun toString(): String = "TotpStanding(totp=$totp, codes=$codes)"

    /**
     * A reader of [TotpStanding], one method per case.
     */
    public interface Report<out T> {
        /**
         * Nothing is set up, or a first seed was begun and its first code was never typed.
         */
        public fun off(): T

        /**
         * The seed works; [codesLeft] recovery codes are unspent.
         */
        public fun active(codesLeft: Int): T

        /**
         * The seed does not work, whether it was retired when a recovery code was spent or replaced by a
         * seed begun again and not yet confirmed; [codesLeft] recovery codes are unspent.
         */
        public fun retired(codesLeft: Int): T
    }
}
