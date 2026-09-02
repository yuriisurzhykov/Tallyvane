package tallyvane.identity.domain

/**
 * The whole string a caller presents as a bearer credential — `access_<43 base64url characters>`
 * or `refresh_<43 base64url characters>`, 43 being exactly what 32 bytes of CSPRNG output become
 * once base64url-encoded without padding.
 *
 * Never stored as-is: this is the value that exists only between a `TokenFactory` minting it and a
 * `TokenHasher` turning it into a [HashedToken] for storage or comparison. The shape is validated
 * here, at construction, rather than trusted from whichever port handed the string over — the same
 * idiom `platform:http`'s `BasePath` already uses for a value whose caller might get it wrong.
 *
 * The error message below names no part of the offending value. A malformed [TokenValue] is, in
 * the cases that matter, a real bearer credential that failed to parse — reporting it verbatim
 * would put a live secret's fragments into a log or a stack trace, which §17 forbids of every
 * token in this system.
 */
@JvmInline
public value class TokenValue(public val raw: String) {
    init {
        require(SHAPE.matches(raw)) {
            "A token value must be <kind>_<43 base64url characters>; the given value was not"
        }
    }

    private companion object {
        val SHAPE = Regex("^[a-z]+_[A-Za-z0-9_-]{43}$")
    }
}
