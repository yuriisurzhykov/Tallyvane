package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.SecondFactorMethod
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

class EmailSecondFactorSpec :
    StringSpec({
        "email factor only consumes an unexpired MFA code bound to this pending authentication" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
            val pendingBinding = "pending-123"
            val email = Email("person@example.test")
            val users = UserRepositoryFake()
            users.insert(User(userId, email, null, NOW, null, emailVerified = true))
            val challenges = EmailChallenges(
                MfaChallengeStore(),
                object : EmailDelivery {
                    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) = Unit
                },
                Codes(),
                TransactionRunnerFake(),
                IdGeneratorFake(),
                ClockFake(NOW),
            )
            val enrollment = EnrolledEmailMfa()
            enrollment.enroll(userId)
            val method = SecondFactorMethod.EmailOtp(users, enrollment, challenges)
            val challenge = challenges.issue(email, EmailChallengePurpose.MFA, pendingBinding)!!

            method.isEnrolledFor(userId) shouldBe true
            method.verify(userId, SecondFactorProof("314159", challenge.id, pendingBinding)) shouldBe false
            method.verify(userId, SecondFactorProof("123456", challenge.id, "other-pending")) shouldBe false
            method.verify(userId, SecondFactorProof("123456", challenge.id, pendingBinding)) shouldBe true
            method.verify(userId, SecondFactorProof("123456", challenge.id, pendingBinding)) shouldBe false
        }
    })

private class EnrolledEmailMfa : EmailMfaEnrollmentStore {
    private val users = mutableSetOf<UserId>()
    override suspend fun enroll(userId: UserId) {
        users += userId
    }
    override suspend fun unenroll(userId: UserId) {
        users -= userId
    }
    override suspend fun isEnrolled(userId: UserId): Boolean = userId in users
}

private class MfaChallengeStore : EmailChallengeStore {
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

private class Codes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("backup")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}

private val NOW = Instant.parse("2026-01-01T00:00:00Z")
