package tallyvane.identity.domain.secondfactor

/**
 * A secret that must be read back — a TOTP seed, not a password or token — after
 * [tallyvane.identity.application.port.SecretCipher.encrypt].
 *
 * Base64-encoded text, not a raw [ByteArray]: a `data class`/`value class` over a `ByteArray`
 * compares by reference, not content, an equality mistake worth avoiding rather than working
 * around at every call site — the same reason [tallyvane.identity.domain.token.HashedToken] wraps
 * a comparable value rather than the bytes a hash function actually produces.
 */
@JvmInline
public value class EncryptedSecret(public val value: String)
