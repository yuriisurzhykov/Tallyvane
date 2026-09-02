package tallyvane.identity.application.port

import tallyvane.identity.domain.TokenKind
import tallyvane.identity.domain.TokenValue

/**
 * A [TokenFactory] that yields a known, deterministic sequence instead of real randomness, so a
 * test can name the value it expects.
 */
internal class TokenFactoryFake : TokenFactory {
    private var sequence = 0

    override fun mint(kind: TokenKind): TokenValue {
        sequence += 1
        return TokenValue("${kind.prefix}_" + sequence.toString().padStart(RANDOM_LENGTH, '0'))
    }

    private companion object {
        const val RANDOM_LENGTH = 43
    }
}
