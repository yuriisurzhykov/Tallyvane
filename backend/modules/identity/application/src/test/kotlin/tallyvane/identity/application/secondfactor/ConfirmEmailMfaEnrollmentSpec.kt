package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConfirmEmailMfaEnrollmentSpec :
    StringSpec({
        "confirmation enrolls only after consuming this user's MFA challenge once" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
            val email = Email("person@example.test")
            val users = UserRepositoryFake()
            users.insert(User(userId, email, null, NOW, null, emailVerified = true))
            val store = ConfirmChallengeStore()
            val challenges = EmailChallenges(
                store,
                object : EmailDelivery {
                    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) = Unit
                },
                ConfirmCodes(),
                TransactionRunnerFake(),
                IdGeneratorFake(),
                ClockFake(NOW),
            )
            val challenge = challenges.issue(
                email,
                EmailChallengePurpose.MFA,
                BeginEmailMfaEnrollmentUseCase.enrollmentBinding(userId),
            )!!
            val enrolled = ConfirmEnrollmentStore()
            val confirm = ConfirmEmailMfaEnrollmentUseCase.Confirm(users, enrolled, challenges, TransactionRunnerFake())

            confirm.confirm(userId, challenge.id, Secret("123456")) shouldBe true
            enrolled.isEnrolled(userId) shouldBe true
            confirm.confirm(userId, challenge.id, Secret("123456")) shouldBe false
        }
    })

private class ConfirmEnrollmentStore : EmailMfaEnrollmentStore {
    private val users = mutableSetOf<UserId>()
    override suspend fun enroll(userId: UserId) {
        users += userId
    }
    override suspend fun unenroll(userId: UserId) {
        users -= userId
    }
    override suspend fun isEnrolled(userId: UserId) = userId in users
}

private class ConfirmChallengeStore : EmailChallengeStore {
    private val records = mutableMapOf<Uuid, Pair<EmailChallenge, Secret>>()
    override suspend fun issue(
        challenge: EmailChallenge,
        hash: Secret,
        now: Instant,
        resendAt: Instant,
        maxAttempts: Int,
    ): Boolean {
        records[challenge.id] = challenge to hash
        return true
    }
    override suspend fun find(id: Uuid): EmailChallenge? = records[id]?.first
    override suspend fun consume(id: Uuid, hash: Secret, now: Instant): Boolean =
        records[id]?.takeIf { (challenge, expected) -> challenge.expiresAt >= now && expected == hash }
            ?.let { records.remove(id) != null } ?: false
    override suspend fun revoke(id: Uuid) {
        records.remove(id)
    }
}

private class ConfirmCodes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("backup")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}

private val NOW = Instant.parse("2026-01-01T00:00:00Z")
