package tallyvane.identity.application.port

import tallyvane.identity.domain.TokenKind
import tallyvane.identity.domain.TokenValue
import java.security.SecureRandom
import java.util.Base64

/**
 * Mints a fresh, unguessable [TokenValue] — the only place in this module allowed to construct one
 * by hand (Factory Method).
 *
 * Kept separate from `TokenHasher` on purpose: minting has to change if the token's own shape ever
 * changes, hashing has to change if the pepper scheme does, and those are two different reasons for
 * two different classes to change.
 */
public interface TokenFactory {
    /**
     * @param kind which half of a pair this token will be — stamped into its own prefix, so a
     * value arriving at any later port can be told apart without a second parameter naming it.
     */
    public fun mint(kind: TokenKind): TokenValue

    /**
     * Draws its randomness from [java.security.SecureRandom] — a CSPRNG, not
     * [tallyvane.platform.kernel.IdGenerator]: a UUID publishes its own mint time and spends at
     * most 74 bits on randomness, which ADR-047 already names as unfit for anything that must be
     * unguessable. `no-ambient-random` does not cover `SecureRandom` either, and rightly not — that
     * rule exists to keep domain and application code off *ambient* non-determinism reached for
     * directly, and this class is the one deliberate, reviewed place that reaches for it, the same
     * shape [tallyvane.platform.kernel.Clock.Wall] already has for wall-clock time.
     *
     * Reaches no technology beyond the JDK's own platform-provided randomness source, so it nests
     * on the port rather than living in an `infrastructure` layer identity does not otherwise need
     * for this concern (ADR-047).
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
