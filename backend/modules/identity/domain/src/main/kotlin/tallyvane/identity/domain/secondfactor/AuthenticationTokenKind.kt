package tallyvane.identity.domain.secondfactor

/** A configured proof method; distinct from access and refresh session tokens. */
public enum class AuthenticationTokenKind {
    PASSWORD,
    GOOGLE,
    EMAIL_SIGN_IN_CODE,
    TOTP,
    EMAIL_FACTOR_CODE,
    BACKUP_CODE;

    public val isPrimary: Boolean
        get() = this in PRIMARY

    public val isSecondFactor: Boolean
        get() = this in SECOND_FACTORS

    public companion object {
        private val PRIMARY = setOf(PASSWORD, GOOGLE, EMAIL_SIGN_IN_CODE)
        private val SECOND_FACTORS = setOf(TOTP, EMAIL_FACTOR_CODE, BACKUP_CODE)
    }
}
