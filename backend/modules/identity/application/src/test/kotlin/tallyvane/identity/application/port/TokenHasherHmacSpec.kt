package tallyvane.identity.application.port

import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.token.TokenKind
import tallyvane.platform.kernel.Secret

/**
 * [TokenHasherConformance] covers what every [TokenHasher] must do; this adds only what is
 * actually specific to [TokenHasher.Hmac] — a real dependence on the pepper's own value, which
 * [TokenHasherFake] does not model.
 */
class TokenHasherHmacSpec : TokenHasherConformance() {
    override fun fresh(pepperVersion: Int): TokenHasher = TokenHasher.Hmac(Secret("pepper-value"), pepperVersion)

    init {
        "the same token hashed under a different pepper value does not match" {
            val token = TokenFactory.Csprng().mint(TokenKind.ACCESS)
            val hashed = TokenHasher.Hmac(pepper = Secret("pepper-a"), pepperVersion = 1).hash(token)

            TokenHasher.Hmac(pepper = Secret("pepper-b"), pepperVersion = 1).matches(token, hashed) shouldBe false
        }

        "hashing does not leak the raw token into the hash's own string form" {
            val token = TokenFactory.Csprng().mint(TokenKind.ACCESS)
            val hasher = TokenHasher.Hmac(pepper = Secret("pepper-value"), pepperVersion = 1)

            hasher.hash(token).toString().contains(token.raw) shouldBe false
        }
    }
}
