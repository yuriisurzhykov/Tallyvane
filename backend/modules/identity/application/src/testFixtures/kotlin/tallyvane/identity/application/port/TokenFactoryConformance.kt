package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.identity.domain.token.TokenKind

/**
 * The behaviour every [TokenFactory] must show, whatever mints the value.
 *
 * Written once and inherited by each implementation's spec: the fake in this module's own
 * `src/test`, and [TokenFactory.Csprng] here. Both must stamp the right prefix for the requested
 * [TokenKind] and mint a value shaped exactly as [tallyvane.identity.domain.token.TokenValue] itself
 * requires — the two properties every later port depends on without re-deriving them (ADR-046).
 */
public abstract class TokenFactoryConformance : StringSpec() {
    protected abstract fun fresh(): TokenFactory

    init {
        "mints an access token with the access prefix" {
            fresh().mint(TokenKind.ACCESS).raw.startsWith("access_") shouldBe true
        }

        "mints a refresh token with the refresh prefix" {
            fresh().mint(TokenKind.REFRESH).raw.startsWith("refresh_") shouldBe true
        }

        "mints a random part exactly as long as TokenValue itself requires" {
            fresh().mint(TokenKind.ACCESS).raw.removePrefix("access_").length shouldBe RANDOM_LENGTH
        }

        "mints a different value on every call" {
            val factory = fresh()
            factory.mint(TokenKind.ACCESS) shouldNotBe factory.mint(TokenKind.ACCESS)
        }
    }

    private companion object {
        const val RANDOM_LENGTH = 43
    }
}
