package tallyvane.identity.application.session

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RevokeSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val otherUserId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
        val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000003"))
        val now = Instant.parse("2026-02-01T00:00:00Z")
        val session = Session(
            id = sessionId,
            userId = userId,
            device = DeviceLabel("Chrome on MacBook"),
            tokenFamilyId = TokenFamilyId(Uuid.parse("00000000-0000-7000-8000-000000000004")),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastUsedAt = Instant.parse("2026-01-01T00:00:00Z"),
            revokedAt = null,
        )

        fun revoke(sessions: SessionStoreFake, refreshTokens: RefreshTokenStoreFake = RefreshTokenStoreFake()) =
            RevokeSessionUseCase.Revoke(sessions, refreshTokens, ClockFake(now), TransactionRunnerFake())

        "revokes the caller's own session" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }

            val result = revoke(sessions).revoke(userId, sessionId)

            result shouldBe RevokeSessionOutcome.Revoked
            sessions.saved[sessionId]?.revokedAt shouldBe now
        }

        "revoking a session also revokes its refresh tokens" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }
            val refreshTokens = RefreshTokenStoreFake()
            val hash = HashedToken(Secret("token"), 1)
            refreshTokens.issueFirst(sessionId, session.tokenFamilyId, hash, now, now)

            revoke(sessions, refreshTokens).revoke(userId, sessionId)

            refreshTokens.stateOf(hash)?.used shouldBe true
        }

        "an unknown session id is refused, not thrown" {
            val sessions = SessionStoreFake()

            val result = revoke(sessions).revoke(userId, sessionId)

            result shouldBe RevokeSessionOutcome.NotFound
        }

        "a session belonging to a different account is refused, not revoked" {
            val sessions = SessionStoreFake().also { it.saved[sessionId] = session }

            val result = revoke(sessions).revoke(otherUserId, sessionId)

            result shouldBe RevokeSessionOutcome.NotFound
            sessions.saved[sessionId]?.revokedAt.shouldBeNull()
        }
    })
