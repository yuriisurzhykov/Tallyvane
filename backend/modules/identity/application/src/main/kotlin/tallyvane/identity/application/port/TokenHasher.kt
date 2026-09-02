package tallyvane.identity.application.port

import tallyvane.identity.domain.HashedToken
import tallyvane.identity.domain.TokenValue
import tallyvane.platform.kernel.Secret
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Turns a [TokenValue] into the [HashedToken] this module is ever allowed to store, and checks
 * whether a presented value matches one already stored.
 *
 * Separate from [TokenFactory] because minting and hashing are two different reasons to change:
 * this one changes if the pepper scheme changes, not if the token's own shape does.
 */
public interface TokenHasher {
    public fun hash(token: TokenValue): HashedToken

    public fun matches(token: TokenValue, hashed: HashedToken): Boolean

    /**
     * HMAC-SHA256 under [pepper] — the fast primitive the design calls for, deliberately not
     * Argon2id: a 256-bit random token has no guessing surface slow hashing exists to protect
     * against, unlike a human-chosen password, which is what `PasswordHasher` is for instead.
     *
     * Reaches no technology beyond the JDK's own `javax.crypto`, so it nests on the port rather
     * than living in an `infrastructure` layer identity does not otherwise need for this concern —
     * the same reasoning [tallyvane.platform.kernel.Clock.Wall] already rests on (ADR-047).
     *
     * Verifies against exactly one pepper version, [pepperVersion] — this is the known,
     * deliberately deferred limit of this first implementation. A token hashed under a superseded
     * pepper will not verify once a rotation happens; nothing in this pass exercises rotation, so
     * nothing yet needs it to. Where [pepper] itself comes from — a configuration value today,
     * `identity.pepper_versions` once rotation is exercised for real — is a wiring decision for
     * whoever constructs this, not this class's concern.
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
