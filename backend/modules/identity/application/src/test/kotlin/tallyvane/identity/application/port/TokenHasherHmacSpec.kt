package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.TokenKind
import tallyvane.platform.kernel.Secret

class TokenHasherHmacSpec :
    StringSpec({
        "a token matches its own hash" {
            val hasher = TokenHasher.Hmac(pepper = Secret("pepper-value"), pepperVersion = 1)
            val token = TokenFactory.Csprng().mint(TokenKind.ACCESS)

            hasher.matches(token, hasher.hash(token)) shouldBe true
        }

        "a different token does not match" {
            val hasher = TokenHasher.Hmac(pepper = Secret("pepper-value"), pepperVersion = 1)
            val factory = TokenFactory.Csprng()
            val minted = factory.mint(TokenKind.ACCESS)
            val other = factory.mint(TokenKind.ACCESS)

            hasher.matches(other, hasher.hash(minted)) shouldBe false
        }

        "the same token hashed under a different pepper does not match" {
            val token = TokenFactory.Csprng().mint(TokenKind.ACCESS)
            val hashed = TokenHasher.Hmac(pepper = Secret("pepper-a"), pepperVersion = 1).hash(token)

            TokenHasher.Hmac(pepper = Secret("pepper-b"), pepperVersion = 1).matches(token, hashed) shouldBe false
        }

        "a hash carries the pepper version it was produced under" {
            val hasher = TokenHasher.Hmac(pepper = Secret("pepper-value"), pepperVersion = 3)
            hasher.hash(TokenFactory.Csprng().mint(TokenKind.ACCESS)).pepperVersion shouldBe 3
        }

        "a hash produced under one pepper version does not verify against a hasher for another" {
            val token = TokenFactory.Csprng().mint(TokenKind.ACCESS)
            val hashed = TokenHasher.Hmac(pepper = Secret("pepper-value"), pepperVersion = 1).hash(token)

            TokenHasher.Hmac(pepper = Secret("pepper-value"), pepperVersion = 2).matches(token, hashed) shouldBe false
        }

        "hashing does not leak the raw token into the hash's own string form" {
            val token = TokenFactory.Csprng().mint(TokenKind.ACCESS)
            val hasher = TokenHasher.Hmac(pepper = Secret("pepper-value"), pepperVersion = 1)

            hasher.hash(token).toString().contains(token.raw) shouldBe false
        }
    })
