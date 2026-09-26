package tallyvane.identity.domain.session

/**
 * A human-readable name for the device or browser a session was opened from — "Chrome on
 * MacBook", "iPhone Safari" — shown back to the account holder in a "connected devices" list, so
 * revoking a session means choosing a device somebody recognises rather than a random identifier.
 */
@JvmInline
public value class DeviceLabel(public val value: String) {
    init {
        require(value.isNotBlank()) { "A device label must not be blank" }
        require(value.length <= MAX_LENGTH) { "A device label must be at most $MAX_LENGTH characters" }
    }

    private companion object {
        const val MAX_LENGTH = 120
    }
}
