package tallyvane.identity.domain.credential

/**
 * Google's own stable identifier for an account — the OIDC `sub` claim, opaque and, per the spec,
 * never reused for a different user even if the original account is later deleted.
 */
@JvmInline
public value class GoogleSubject(public val value: String) {
    init {
        require(value.isNotBlank()) { "A Google subject must not be blank" }
        require(value.length <= MAX_LENGTH) {
            "A Google subject must be at most $MAX_LENGTH characters (OIDC core 1.0)"
        }
    }

    private companion object {
        const val MAX_LENGTH = 255
    }
}
