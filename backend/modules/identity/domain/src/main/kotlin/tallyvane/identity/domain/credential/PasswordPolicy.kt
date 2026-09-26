package tallyvane.identity.domain.credential

/**
 * Length limits apply to Unicode code points; spaces and all character classes are accepted.
 */
public class PasswordPolicy(
    private val minimumLength: Int = DEFAULT_MINIMUM,
    private val maximumLength: Int = DEFAULT_MAXIMUM,
) {
    init {
        require(minimumLength in 1..maximumLength) { "Invalid password length bounds" }
    }

    public fun accepts(password: String): Boolean =
        password.codePointCount(0, password.length) in minimumLength..maximumLength

    public companion object {
        private const val DEFAULT_MINIMUM: Int = 15
        private const val DEFAULT_MAXIMUM: Int = 128
        public val Default: PasswordPolicy = PasswordPolicy()
    }
}
