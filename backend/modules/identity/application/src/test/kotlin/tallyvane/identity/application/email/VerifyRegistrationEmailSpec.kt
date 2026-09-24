package tallyvane.identity.application.email

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class VerifyRegistrationEmailSpec : StringSpec({
    "only a valid registration-purpose challenge verifies the matching account email" {
        val users = UserRepositoryFake()
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val email = Email("new@example.test")
        users.insert(User(userId, email, null, Instant.parse("2026-01-01T00:00:00Z"), null, false))
        val clock = ClockFake(Instant.parse("2026-01-01T00:00:00Z"))
        val store = ChallengeStore()
        val service = EmailChallenges(store, object : EmailDelivery {
            override suspend fun sendCode(email: Email, purpose: tallyvane.identity.domain.email.EmailChallengePurpose, code: Secret) = Unit
        }, FixedCodes(), TransactionRunnerFake(), IdGeneratorFake(), clock)
        val challenge = service.issue(email, tallyvane.identity.domain.email.EmailChallengePurpose.REGISTRATION, userId.value.toString())!!
        val useCase = VerifyRegistrationEmailUseCase.Verify(users, service, TransactionRunnerFake())

        useCase.verify(userId, challenge.id, email, Secret("123456")) shouldBe true
        users.findById(userId)?.emailVerified shouldBe true
        useCase.verify(userId, challenge.id, email, Secret("123456")) shouldBe false
    }
})

private class ChallengeStore : EmailChallengeStore {
    private val challenges = mutableMapOf<Uuid, EmailChallenge>()
    private val hashes = mutableMapOf<Uuid, Secret>()
    private val remaining = mutableMapOf<Uuid, Int>()
    private val consumed = mutableSetOf<Uuid>()
    override suspend fun issue(challenge: EmailChallenge, hash: Secret, now: Instant, resendAt: Instant, maxAttempts: Int): Boolean {
        challenges[challenge.id] = challenge; hashes[challenge.id] = hash; remaining[challenge.id] = maxAttempts; return true
    }
    override suspend fun find(id: Uuid): EmailChallenge? = challenges[id]
    override suspend fun consume(id: Uuid, hash: Secret, now: Instant): Boolean {
        val challenge = challenges[id] ?: return false
        if (id in consumed || challenge.expiresAt <= now || (remaining[id] ?: 0) <= 0) return false
        remaining[id] = remaining.getValue(id) - 1
        return (hashes[id] == hash).also { if (it) consumed += id }
    }
    override suspend fun revoke(id: Uuid) { consumed += id }
}

private class EmailChallengeStoreFakeConformanceSpec : tallyvane.identity.application.port.EmailChallengeStoreConformance() {
    override fun fresh(): EmailChallengeStore = ChallengeStore()
}

private class FixedCodes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("backup")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}
