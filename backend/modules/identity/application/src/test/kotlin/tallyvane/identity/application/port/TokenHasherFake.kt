package tallyvane.identity.application.port

import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenValue
import tallyvane.platform.kernel.Secret

/**
 * A [TokenHasher] whose "hash" is the raw value itself, so a test can name the value it expects
 * without computing a real HMAC.
 */
internal class TokenHasherFake(private val pepperVersion: Int = 1) : TokenHasher {
    override fun hash(token: TokenValue): HashedToken = HashedToken(Secret(token.raw), pepperVersion)

    override fun matches(token: TokenValue, hashed: HashedToken): Boolean =
        hashed.pepperVersion == pepperVersion && Secret(token.raw) == hashed.hash
}
