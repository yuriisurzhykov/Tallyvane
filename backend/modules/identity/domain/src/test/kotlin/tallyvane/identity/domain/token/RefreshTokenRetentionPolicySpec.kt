package tallyvane.identity.domain.token

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RefreshTokenRetentionPolicySpec :
    StringSpec({
        "the cutoff is exactly the cap subtracted from now" {
            val policy = RefreshTokenRetentionPolicy.Default(90.days)

            policy.cutoff(now = Instant.parse("2026-04-01T00:00:00Z")) shouldBe Instant.parse("2026-01-01T00:00:00Z")
        }

        "a shorter cap moves the cutoff closer to now" {
            val now = Instant.parse("2026-04-01T00:00:00Z")

            RefreshTokenRetentionPolicy.Default(30.days).cutoff(now) shouldBe now - 30.days
        }
    })
