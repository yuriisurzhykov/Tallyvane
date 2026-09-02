package tallyvane.identity.application.port

import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenValue
import tallyvane.platform.kernel.Secret
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Turns a [TokenValue] into the [HashedToken] this module is ever allowed to store, and checks
 * whether a presented value matches one already stored.
 *
 * Separate from [TokenFactory]: this changes if the pepper scheme changes, not if the token's own
 * shape does.
 */
public interface TokenHasher {
    public fun hash(token: TokenValue): HashedToken

    public fun matches(token: TokenValue, hashed: HashedToken): Boolean

    /**
     * HMAC-SHA256 under [pepper] — fast on purpose, since a 256-bit random token has no guessing
     * surface slow hashing exists to protect against (unlike a human-chosen password, which is
     * what `PasswordHasher` is for).
     *
     * Verifies against exactly one [pepperVersion]: a token hashed under a superseded pepper will
     * not verify after a rotation. Why this limit is acceptable for now, and where [pepper] comes
     * from: `application/README.md`.
     */
    public class Hmac(private val pepper: Secret, private val pepperVersion: Int) : TokenHasher {
        override fun hash(token: TokenValue): HashedToken =
            HashedToken(hash = Secret(mac(token.raw)), pepperVersion = pepperVersion)

        override fun matches(token: TokenValue, hashed: HashedToken): Boolean =
            hashed.pepperVersion == pepperVersion && Secret(mac(token.raw)) == hashed.hash

        private fun mac(raw: String): String {
            val mac = Mac.getInstance(ALGORITHM)
            mac.init(SecretKeySpec(pepper.revealed().toByteArray(), ALGORITHM))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(raw.toByteArray()))
        }

        private companion object {
            const val ALGORITHM = "HmacSHA256"
        }
    }
}
