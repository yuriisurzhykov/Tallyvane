package tallyvane.authentication.application

/**
 * What an account has set up as its second factor, as the settings screen needs to say it.
 *
 * Its contents are private, and the way to read them is [reportTo].
 */
public class SecondFactorShown internal constructor(
    private val standing: Standing,
    private val recoveryCodesLeft: Int,
) {
    /**
     * Tells [report] which case this is.
     */
    public fun <T> reportTo(report: Report<T>): T = when (standing) {
        Standing.Off -> report.off()
        Standing.Active -> report.active(recoveryCodesLeft)
        Standing.Retired -> report.retired(recoveryCodesLeft)
    }

    override fun toString(): String = "SecondFactorShown($standing)"

    internal enum class Standing { Off, Active, Retired }

    /**
     * A reader of [SecondFactorShown], one method per case.
     */
    public interface Report<out T> {
        /**
         * TOTP is off, or begun and not yet confirmed.
         */
        public fun off(): T

        /**
         * TOTP is on, with this many recovery codes unspent.
         */
        public fun active(recoveryCodesLeft: Int): T

        /**
         * The seed was retired because a recovery code was spent; this many recovery codes are unspent. The
         * person turns TOTP on again.
         */
        public fun retired(recoveryCodesLeft: Int): T
    }
}
