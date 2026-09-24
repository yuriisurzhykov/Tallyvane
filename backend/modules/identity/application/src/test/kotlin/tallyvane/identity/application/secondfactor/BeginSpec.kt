package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
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

class BeginSpec : StringSpec({
    "email MFA enrollment sends a purpose-bound challenge only after password reauthentication" {
        val id = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val users = UserRepositoryFake()
        users.insert(User(id, Email("person@example.test"), null, NOW, null, emailVerified = true))
        val hasher = PasswordHasherFake()
        val credentials = CredentialRepositoryFake()
        credentials.save(id, Credential.PasswordRecord(hasher.hash(Secret("current password"))))
        val challengeStore = EnrollmentChallengeStore()
        val challenges = EmailChallenges(challengeStore, NoEmailDelivery(), FixedAuthCodes(), TransactionRunnerFake(), IdGeneratorFake(), ClockFake(NOW))
        val begin = BeginEmailMfaEnrollmentUseCase.Begin(users, credentials, hasher, challenges, TransactionRunnerFake())

        begin.begin(id, Secret("wrong password")) shouldBe null
        challengeStore.last shouldBe null
        val challengeId = begin.begin(id, Secret("current password"))
        challengeId shouldBe challengeStore.last?.id
        challengeStore.last?.purpose shouldBe EmailChallengePurpose.MFA
        challengeStore.last?.binding shouldBe BeginEmailMfaEnrollmentUseCase.enrollmentBinding(id)
    }
})

private class EnrollmentChallengeStore : EmailChallengeStore {
    var last: EmailChallenge? = null
    override suspend fun issue(challenge: EmailChallenge, hash: Secret, now: Instant, resendAt: Instant, maxAttempts: Int): Boolean {
        last = challenge
        return true
    }
    override suspend fun find(id: Uuid): EmailChallenge? = last?.takeIf { it.id == id }
    override suspend fun consume(id: Uuid, hash: Secret, now: Instant) = false
    override suspend fun revoke(id: Uuid) = Unit
}

private class NoEmailDelivery : EmailDelivery {
    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) = Unit
}

private class FixedAuthCodes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("backup")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}

private val NOW = Instant.parse("2026-01-01T00:00:00Z")
