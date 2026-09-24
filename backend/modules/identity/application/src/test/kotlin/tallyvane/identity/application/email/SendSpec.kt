package tallyvane.identity.application.email

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SendSpec : StringSpec({
    val now = Instant.parse("2026-01-01T00:00:00Z")
    var issued: EmailChallenge? = null
    val service = EmailChallenges(
        object : EmailChallengeStore {
            override suspend fun issue(challenge: EmailChallenge, hash: Secret, now: Instant, resendAt: Instant, maxAttempts: Int): Boolean {
                issued = challenge
                return true
            }
            override suspend fun find(id: Uuid) = issued?.takeIf { it.id == id }
            override suspend fun consume(id: Uuid, hash: Secret, now: Instant) = false
            override suspend fun revoke(id: Uuid) = Unit
        },
        object : EmailDelivery { override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) = Unit },
        object : AuthenticationCodes {
            override fun emailCode() = Secret("123456")
            override fun backupCode() = Secret("backup")
            override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
        },
        TransactionRunnerFake(), IdGeneratorFake(), ClockFake(now),
    )

    "password recovery code requests create PASSWORD_RESET challenges" {
        val id = RequestPasswordResetUseCase.Send(service).request(Email("person@example.test"))!!
        service.challenge(id)?.purpose shouldBe EmailChallengePurpose.PASSWORD_RESET
    }
})
