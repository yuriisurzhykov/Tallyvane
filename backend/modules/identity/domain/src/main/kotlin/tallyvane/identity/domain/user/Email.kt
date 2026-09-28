package tallyvane.identity.domain.user

/**
 * An email address, validated for shape only (`local@domain.tld`) — not for deliverability, which
 * no regular expression can promise anyway.
 *
 * ```
 * Email("person@example.com")  // ok
 * Email("person@example")      // throws IllegalArgumentException — no top-level domain
 * ```
 *
 * Comparison is case-sensitive despite lookup being case-insensitive in Postgres — why:
 * `domain/README.md`.
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
