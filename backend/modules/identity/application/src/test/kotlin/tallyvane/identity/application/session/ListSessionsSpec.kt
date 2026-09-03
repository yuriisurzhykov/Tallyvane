package tallyvane.identity.application.session

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ListSessionsSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val otherUserId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000002"))

        fun session(id: SessionId, owner: UserId, revokedAt: Instant? = null) = Session(
            id = id,
            userId = owner,
            device = DeviceLabel("Chrome on MacBook"),
            tokenFamilyId = TokenFamilyId(Uuid.random()),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastUsedAt = Instant.parse("2026-01-01T00:00:00Z"),
            revokedAt = revokedAt,
        )

        fun list(sessions: SessionStoreFake) = ListSessionsUseCase.ListSessions(sessions, TransactionRunnerFake())

        "lists only the caller's own sessions, not another account's" {
            val mine = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000010"))
            val theirs = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000011"))
            val sessions = SessionStoreFake().also {
                it.saved[mine] = session(mine, userId)
                it.saved[theirs] = session(theirs, otherUserId)
            }

            val result = list(sessions).list(userId)

            result shouldContainExactlyInAnyOrder listOf(sessions.saved[mine])
        }

        "includes an already-revoked session, rather than hiding it" {
            val revoked = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000010"))
            val sessions = SessionStoreFake().also {
                it.saved[revoked] = session(revoked, userId, revokedAt = Instant.parse("2026-02-01T00:00:00Z"))
            }

            val result = list(sessions).list(userId)

            result shouldContain sessions.saved[revoked]
        }

        "an account with no sessions gets an empty list" {
            val sessions = SessionStoreFake()

            list(sessions).list(userId) shouldBe emptyList()
        }
    })
