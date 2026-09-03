package tallyvane.identity.application.session

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.RefreshTokenRetentionPolicy
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

private fun testHash(): HashedToken = HashedToken(Secret(Uuid.random().toString()), 1)

class PruneSpec :
    StringSpec({
        val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val familyId = TokenFamilyId(Uuid.parse("00000000-0000-7000-8000-000000000002"))

        fun prune(refreshTokens: RefreshTokenStoreFake, now: Instant, cap: Duration = 90.days) =
            PruneExpiredRefreshTokens.Default(
                refreshTokens = refreshTokens,
                retention = RefreshTokenRetentionPolicy.Default(cap),
                clock = ClockFake(now),
                transactions = TransactionRunnerFake(),
            )

        "deletes a token issued before the cap, and reports how many" {
            val refreshTokens = RefreshTokenStoreFake()
            val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
            refreshTokens.issueFirst(sessionId, familyId, testHash(), issuedAt + 30.days, issuedAt)

            val deleted = prune(refreshTokens, now = issuedAt + 91.days).prune()

            deleted shouldBe 1
        }

        "leaves a token issued after the cap alone" {
            val refreshTokens = RefreshTokenStoreFake()
            val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
            val hash = testHash()
            refreshTokens.issueFirst(sessionId, familyId, hash, issuedAt + 30.days, issuedAt)

            prune(refreshTokens, now = issuedAt + 89.days).prune()

            refreshTokens.stateOf(hash)?.used shouldBe false
        }
    })
