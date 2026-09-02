package tallyvane.identity.application.port

import tallyvane.identity.domain.token.TokenKind
import tallyvane.identity.domain.token.TokenValue
import java.security.SecureRandom
import java.util.Base64

/**
 * Mints a fresh, unguessable [TokenValue] — the only place in this module allowed to construct one
 * by hand.
 *
 * Kept separate from `TokenHasher`: minting changes if the token's own shape changes, hashing
 * changes if the pepper scheme does — two different reasons to change.
 */
public interface TokenFactory {
    /**
     * @param kind Which half of a pair this token will be — stamped into its own prefix.
     */
    public fun mint(kind: TokenKind): TokenValue

    /**
     * Draws its randomness from [java.security.SecureRandom], not
     * [tallyvane.platform.kernel.IdGenerator] — why, and why this reaches for it directly despite
     * `no-ambient-random`: `application/README.md`.
     */
    public class Csprng : TokenFactory {
        private val random = SecureRandom()

        override fun mint(kind: TokenKind): TokenValue {
            val bytes = ByteArray(RANDOM_BYTES)
            random.nextBytes(bytes)
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            return TokenValue("${kind.prefix}_$encoded")
        }

        private companion object {
            const val RANDOM_BYTES = 32
        }
    }
}
