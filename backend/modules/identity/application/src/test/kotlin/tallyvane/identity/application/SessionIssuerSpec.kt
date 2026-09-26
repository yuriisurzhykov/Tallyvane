package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.contract.Principal
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid
import tallyvane.identity.contract.UserId as ContractUserId

class SessionIssuerSpec :
    StringSpec({
        fun issuer(
            sessions: SessionStoreFake = SessionStoreFake(),
            refreshTokens: RefreshTokenStoreFake = RefreshTokenStoreFake(),
        ) = SessionIssuer.Default(
            sessions = sessions,
            refreshTokens = refreshTokens,
            tokenFactory = TokenFactoryFake(),
            tokenHasher = TokenHasherFake(),
            clock = ClockFake(Instant.parse("2026-01-01T00:00:00Z")),
            ids = IdGeneratorFake(),
            accessTokenTtl = 15.minutes,
            refreshTokenIdleTtl = 30.days,
        )

        val principal = Principal.User(ContractUserId(Uuid.parse("00000000-0000-7000-8000-000000000001")))

        "persists the session it issues" {
            val sessions = SessionStoreFake()

            val issued = issuer(sessions).issue(principal, DeviceLabel("Chrome on MacBook"))

            sessions.saved.values.toList() shouldBe listOf(issued.session)
        }

        "returns a well-formed access and refresh token" {
            val issued = issuer().issue(principal, DeviceLabel("Chrome on MacBook"))

            issued.tokens.access.raw.startsWith("access_") shouldBe true
            issued.tokens.refresh.raw.startsWith("refresh_") shouldBe true
        }

        "records the session under the principal's own user id" {
            val issued = issuer().issue(principal, DeviceLabel("Chrome on MacBook"))

            issued.session.userId.value shouldBe principal.id.value
        }

        "records the device label given, unchanged" {
            val issued = issuer().issue(principal, DeviceLabel("iPhone Safari"))

            issued.session.device shouldBe DeviceLabel("iPhone Safari")
        }

        "stamps createdAt and lastUsedAt with the clock's own time, freshly issued" {
            val issued = issuer().issue(principal, DeviceLabel("Chrome on MacBook"))

            issued.session.createdAt shouldBe Instant.parse("2026-01-01T00:00:00Z")
            issued.session.lastUsedAt shouldBe issued.session.createdAt
        }

        "issues a session that is not revoked" {
            val issued = issuer().issue(principal, DeviceLabel("Chrome on MacBook"))

            issued.session.revokedAt shouldBe null
        }

        "issuing twice for the same user produces two distinct sessions" {
            val sessions = SessionStoreFake()
            val issue = issuer(sessions)

            issue.issue(principal, DeviceLabel("Chrome on MacBook"))
            issue.issue(principal, DeviceLabel("iPhone Safari"))

            sessions.saved.values.map { it.id }.distinct() shouldBe sessions.saved.values.map { it.id }
            sessions.saved.size shouldBe 2
        }

        "the issued access token can be found back by its own hash" {
            val sessions = SessionStoreFake()

            val issued = issuer(sessions).issue(principal, DeviceLabel("Chrome on MacBook"))

            val checkedAt = Instant.parse("2026-01-01T00:00:00Z")
            sessions.findByAccessTokenHash(TokenHasherFake().hash(issued.tokens.access), checkedAt) shouldBe
                issued.session
        }

        "the first refresh token is recorded as not yet used, under the session's own family" {
            val refreshTokens = RefreshTokenStoreFake()

            val issued = issuer(refreshTokens = refreshTokens).issue(principal, DeviceLabel("Chrome on MacBook"))

            val state = refreshTokens.stateOf(TokenHasherFake().hash(issued.tokens.refresh))
            state?.sessionId shouldBe issued.session.id
            state?.used shouldBe false
        }
    })
