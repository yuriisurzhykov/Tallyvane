package tallyvane.identity.application.session

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.RefreshRotationPolicy
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.token.TokenKind
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RefreshSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
        val familyId = TokenFamilyId(Uuid.parse("00000000-0000-7000-8000-000000000003"))
        val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
        val session = Session(
            id = sessionId,
            userId = userId,
            device = DeviceLabel("Chrome on MacBook"),
            tokenFamilyId = familyId,
            createdAt = issuedAt,
            lastUsedAt = issuedAt,
            revokedAt = null,
        )
        val factory = TokenFactoryFake()
        val hasher = TokenHasherFake()

        fun refresh(
            sessions: SessionStoreFake,
            refreshTokens: RefreshTokenStoreFake,
            now: Instant = Instant.parse("2026-01-02T00:00:00Z"),
        ) = RefreshSessionUseCase.Refresh(
            refreshTokens = refreshTokens,
            sessions = sessions,
            policy = RefreshRotationPolicy.Default(),
            tokenFactory = factory,
            tokenHasher = hasher,
            clock = ClockFake(now),
            transactions = TransactionRunnerFake(),
            accessTokenTtl = 15.minutes,
            refreshTokenIdleTtl = 30.days,
        )

        "an unknown token is refused, not thrown" {
            val result = refresh(SessionStoreFake(), RefreshTokenStoreFake())
                .refresh(factory.mint(TokenKind.REFRESH))

            result shouldBe RefreshSessionOutcome.Invalid
        }

        "an active refresh token is redeemed for a fresh pair, under the same session" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }
            val refreshTokens = RefreshTokenStoreFake()
            val presented = factory.mint(TokenKind.REFRESH)
            refreshTokens.issueFirst(
                sessionId,
                familyId,
                hasher.hash(presented),
                Instant.parse("2026-02-01T00:00:00Z"),
                issuedAt,
            )

            val result = refresh(sessions, refreshTokens).refresh(presented)

            val issued = result.shouldBeInstanceOf<RefreshSessionOutcome.Issued>()
            issued.sessionId shouldBe sessionId
        }

        "redeeming a token marks it used, so presenting the same value again is treated as reuse" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }
            val refreshTokens = RefreshTokenStoreFake()
            val presented = factory.mint(TokenKind.REFRESH)
            refreshTokens.issueFirst(
                sessionId,
                familyId,
                hasher.hash(presented),
                Instant.parse("2026-02-01T00:00:00Z"),
                issuedAt,
            )
            refresh(sessions, refreshTokens).refresh(presented)

            val result = refresh(sessions, refreshTokens).refresh(presented)

            result shouldBe RefreshSessionOutcome.ReuseDetected
        }

        "the newly minted refresh token, unlike the one just spent, redeems successfully" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }
            val refreshTokens = RefreshTokenStoreFake()
            val presented = factory.mint(TokenKind.REFRESH)
            refreshTokens.issueFirst(
                sessionId,
                familyId,
                hasher.hash(presented),
                Instant.parse("2026-02-01T00:00:00Z"),
                issuedAt,
            )
            val first = refresh(
                sessions,
                refreshTokens,
            ).refresh(presented).shouldBeInstanceOf<RefreshSessionOutcome.Issued>()

            val second = refresh(sessions, refreshTokens).refresh(first.tokens.refresh)

            second.shouldBeInstanceOf<RefreshSessionOutcome.Issued>()
        }

        "a token already consumed by an earlier rotation revokes the whole session" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }
            val refreshTokens = RefreshTokenStoreFake()
            val presented = factory.mint(TokenKind.REFRESH)
            refreshTokens.issueFirst(
                sessionId,
                familyId,
                hasher.hash(presented),
                Instant.parse("2026-02-01T00:00:00Z"),
                issuedAt,
            )
            // A legitimate rotation that already happened, without this use case's own involvement.
            refreshTokens.rotate(
                hasher.hash(presented),
                hasher.hash(factory.mint(TokenKind.REFRESH)),
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-01-01T12:00:00Z"),
            )

            val result = refresh(sessions, refreshTokens).refresh(presented)

            result shouldBe RefreshSessionOutcome.ReuseDetected
            sessions.saved[sessionId]?.revokedAt shouldBe Instant.parse("2026-01-02T00:00:00Z")
        }

        "reuse detection revokes the successor token too, not only the one presented twice" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }
            val refreshTokens = RefreshTokenStoreFake()
            val presented = factory.mint(TokenKind.REFRESH)
            refreshTokens.issueFirst(
                sessionId,
                familyId,
                hasher.hash(presented),
                Instant.parse("2026-02-01T00:00:00Z"),
                issuedAt,
            )
            val successor = factory.mint(TokenKind.REFRESH)
            refreshTokens.rotate(
                hasher.hash(presented),
                hasher.hash(successor),
                Instant.parse("2026-03-01T00:00:00Z"),
                issuedAt,
            )

            refresh(sessions, refreshTokens).refresh(presented)

            refreshTokens.stateOf(hasher.hash(successor))?.used shouldBe true
        }

        "issuing a new pair attaches a fresh access token to the session, not the caller's own" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }
            val refreshTokens = RefreshTokenStoreFake()
            val presented = factory.mint(TokenKind.REFRESH)
            refreshTokens.issueFirst(
                sessionId,
                familyId,
                hasher.hash(presented),
                Instant.parse("2026-02-01T00:00:00Z"),
                issuedAt,
            )
            val now = Instant.parse("2026-01-02T00:00:00Z")

            val result =
                refresh(
                    sessions,
                    refreshTokens,
                    now,
                ).refresh(presented).shouldBeInstanceOf<RefreshSessionOutcome.Issued>()

            sessions.findByAccessTokenHash(hasher.hash(result.tokens.access), now) shouldBe
                session.copy(lastUsedAt = now)
        }
    })
