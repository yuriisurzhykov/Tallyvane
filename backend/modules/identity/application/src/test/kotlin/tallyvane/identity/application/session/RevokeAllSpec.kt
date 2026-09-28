package tallyvane.identity.application.session

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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

class RevokeAllSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val otherUserId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
        val now = Instant.parse("2026-02-01T00:00:00Z")

        fun session(id: SessionId, owner: UserId) = Session(
            id = id,
            userId = owner,
            device = DeviceLabel("Chrome on MacBook"),
            tokenFamilyId = TokenFamilyId(Uuid.random()),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastUsedAt = Instant.parse("2026-01-01T00:00:00Z"),
            revokedAt = null,
        )

        fun revokeAll(sessions: SessionStoreFake, refreshTokens: RefreshTokenStoreFake = RefreshTokenStoreFake()) =
            RevokeAllSessionsUseCase.RevokeAll(sessions, refreshTokens, ClockFake(now), TransactionRunnerFake())

        "revokes every one of the caller's sessions, and returns their ids" {
            val first = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000010"))
            val second = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000011"))
            val sessions = SessionStoreFake().also {
                it.saved[first] = session(first, userId)
                it.saved[second] = session(second, userId)
            }

            val revokedIds = revokeAll(sessions).revokeAll(userId)

            revokedIds shouldContainExactlyInAnyOrder listOf(first, second)
            sessions.saved.values.all { it.revokedAt == now } shouldBe true
        }

        "leaves a different account's sessions untouched" {
            val mine = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000010"))
            val theirs = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000011"))
            val sessions = SessionStoreFake().also {
                it.saved[mine] = session(mine, userId)
                it.saved[theirs] = session(theirs, otherUserId)
            }

            revokeAll(sessions).revokeAll(userId)

            sessions.saved[theirs]?.revokedAt shouldBe null
        }

        "revokes the refresh tokens of every session it revoked" {
            val id = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000010"))
            val theSession = session(id, userId)
            val sessions = SessionStoreFake().also { it.saved[id] = theSession }
            val refreshTokens = RefreshTokenStoreFake()
            val hash = HashedToken(Secret("token"), 1)
            refreshTokens.issueFirst(id, theSession.tokenFamilyId, hash, now, now)

            revokeAll(sessions, refreshTokens).revokeAll(userId)

            refreshTokens.stateOf(hash)?.used shouldBe true
        }

        "an account with no sessions revokes nothing and returns an empty list" {
            val sessions = SessionStoreFake()

            revokeAll(sessions).revokeAll(userId) shouldBe emptyList()
        }
    })
