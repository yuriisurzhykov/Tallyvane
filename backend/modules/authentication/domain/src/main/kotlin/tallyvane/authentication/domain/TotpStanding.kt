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
 * Not a `data class`: nothing outside decides a standing, it comes from [of] only.
 */
public class TotpStanding private constructor(private val kind: Kind, private val codesLeft: Int) {
    /**
     * Tells [report] which case this is, with the recovery codes still unspent where that matters.
     */
    public fun <T> reportTo(report: Report<T>): T = when (kind) {
        Kind.Off -> report.off()
        Kind.Active -> report.active(codesLeft)
        Kind.Retired -> report.retired(codesLeft)
    }

    override fun toString(): String = "TotpStanding($kind)"

    private enum class Kind { Off, Active, Retired }

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

    public companion object {
        /**
         * The standing of an account with [totp] and [codes], either of which it may not have.
         */
        public fun of(totp: TotpEnrollment?, codes: RecoveryCodes?): TotpStanding {
            val left = codes?.remaining() ?: 0
            return when {
                totp == null -> TotpStanding(Kind.Off, 0)
                totp.isActive() -> TotpStanding(Kind.Active, left)
                totp.isRetired() -> TotpStanding(Kind.Retired, left)
                codes != null -> TotpStanding(Kind.Retired, left)
                else -> TotpStanding(Kind.Off, 0)
            }
        }
    }
}
