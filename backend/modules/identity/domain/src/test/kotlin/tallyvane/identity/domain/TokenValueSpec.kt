package tallyvane.identity.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TokenValueSpec :
    StringSpec({
        "accepts a well-formed access token" {
            val raw = "access_" + "a".repeat(43)
            TokenValue(raw).raw shouldBe raw
        }

        "accepts a well-formed refresh token" {
            val raw = "refresh_" + "A".repeat(43)
            TokenValue(raw).raw shouldBe raw
        }

        "rejects a value with no underscore separator" {
            shouldThrow<IllegalArgumentException> { TokenValue("access" + "a".repeat(43)) }
        }

        "rejects a value whose random part is too short" {
            shouldThrow<IllegalArgumentException> { TokenValue("access_" + "a".repeat(42)) }
        }

        "rejects a value whose random part is too long" {
            shouldThrow<IllegalArgumentException> { TokenValue("access_" + "a".repeat(44)) }
        }

        "rejects a value whose prefix has an uppercase letter" {
            shouldThrow<IllegalArgumentException> { TokenValue("Access_" + "a".repeat(43)) }
        }

        "does not name the offending value in its message, since a malformed value may still be a live secret" {
            val secretish = "access_" + "s".repeat(42)
            val failure = shouldThrow<IllegalArgumentException> { TokenValue(secretish) }
            failure.message?.contains(secretish) shouldBe false
        }
    })
