package tallyvane.identity.domain.user

/**
 * An email address, validated for shape only — not for deliverability, which no regular
 * expression can promise anyway.
 *
 * Comparing two [Email] values here is case-sensitive, on purpose: this project's own persistence
 * skill settled case-insensitive lookup as a Postgres column collation
 * (`platform.case_insensitive`), not as normalisation inside the value object. Folding case here
 * too would give this type and the database two different, silently disagreeing notions of
 * "the same address".
 */
@JvmInline
public value class Email(public val value: String) {
    init {
        require(SHAPE.matches(value)) {
            "An email address must match the shape local@domain.tld"
        }
    }

    private companion object {
        val SHAPE = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}
