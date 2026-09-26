package tallyvane.identity.domain.secondfactor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.UserId
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PendingAuthenticationSpec :
    StringSpec({
        val id = PendingAuthenticationId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
        val device = DeviceLabel("Chrome on MacBook")
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")
        val expiresAt = Instant.parse("2026-01-01T00:05:00Z")

        "one or more available methods construct normally" {
            val pending =
                PendingAuthentication(id, userId, device, setOf(SecondFactorKind.TOTP), createdAt, expiresAt)

            pending.availableMethods shouldBe setOf(SecondFactorKind.TOTP)
        }

        "no available methods is refused — nothing could ever complete it" {
            shouldThrow<IllegalArgumentException> {
                PendingAuthentication(id, userId, device, emptySet(), createdAt, expiresAt)
            }
        }
    })
