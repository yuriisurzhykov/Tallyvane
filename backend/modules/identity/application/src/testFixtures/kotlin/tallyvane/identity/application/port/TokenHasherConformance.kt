package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.token.TokenKind

/**
 * The behaviour every [TokenHasher] must show, whatever mechanism produces the hash.
 *
 * Written once and inherited by each implementation's own spec: the fake in this module's own
 * `src/test`, and [TokenHasher.Hmac] there too (ADR-046). [TokenHasherHmacSpec] keeps what is
 * genuinely specific to HMAC — that a *different pepper value* under the same version changes the
 * hash — because [TokenHasherFake] does not model a pepper's value at all, only its version, so
 * that property is not something every conforming implementation has to share.
 */
public abstract class TokenHasherConformance : StringSpec() {
    protected abstract fun fresh(pepperVersion: Int): TokenHasher

    init {
        "a token matches its own hash" {
            val hasher = fresh(pepperVersion = 1)
            val token = TokenFactory.Csprng().mint(TokenKind.ACCESS)

            hasher.matches(token, hasher.hash(token)) shouldBe true
        }

        "a different token does not match" {
            val hasher = fresh(pepperVersion = 1)
            val factory = TokenFactory.Csprng()
            val minted = factory.mint(TokenKind.ACCESS)
            val other = factory.mint(TokenKind.ACCESS)

            hasher.matches(other, hasher.hash(minted)) shouldBe false
        }

        "a hash carries the pepper version it was produced under" {
            val hasher = fresh(pepperVersion = 3)

            hasher.hash(TokenFactory.Csprng().mint(TokenKind.ACCESS)).pepperVersion shouldBe 3
        }

        "a hash produced under one pepper version does not verify against a hasher for another" {
            val token = TokenFactory.Csprng().mint(TokenKind.ACCESS)
            val hashed = fresh(pepperVersion = 1).hash(token)

            fresh(pepperVersion = 2).matches(token, hashed) shouldBe false
        }
    }
}
