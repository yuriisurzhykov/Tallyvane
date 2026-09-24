package tallyvane.identity.application.email

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.PasswordHash
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

class ReplaceSpec : StringSpec({
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val email = Email("person@example.test")
    val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000031"))
    val code = Secret("123456")

    fun challenges() = EmailChallenges(
        ResetStore(), object : EmailDelivery {
            override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) = Unit
        }, ResetCodes(), TransactionRunnerFake(), IdGeneratorFake(), ClockFake(now),
    )

    "a valid reset challenge replaces the password credential" {
        val users = UserRepositoryFake()
        users.insert(User(userId, email, null, now, null, emailVerified = true))
        val credentials = CredentialRepositoryFake()
        credentials.save(userId, Credential.PasswordRecord(PasswordHash(Secret("old-hash"))))
        val challenges = challenges()
        val challenge = challenges.issue(email, EmailChallengePurpose.PASSWORD_RESET)!!
        val hasher = object : PasswordHasher {
            override fun hash(raw: Secret) = PasswordHash(Secret("hash:${raw.revealed()}"))
            override fun verify(raw: Secret, hash: PasswordHash) = false
        }
        val reset = ResetPasswordUseCase.Replace(users, credentials, hasher, challenges, TransactionRunnerFake())

        reset.reset(ResetPasswordRequest(challenge.id, email, code, Secret("a new memorable passphrase"))) shouldBe true
        credentials.findPasswordFor(userId)?.hash shouldBe PasswordHash(Secret("hash:a new memorable passphrase"))
        challenges.verify(challenge.id, email, EmailChallengePurpose.PASSWORD_RESET, code) shouldBe false
    }

    "a valid reset request has the same accepted result when the account does not exist" {
        val challenges = challenges()
        val challenge = challenges.issue(email, EmailChallengePurpose.PASSWORD_RESET)!!
        val reset = ResetPasswordUseCase.Replace(
            UserRepositoryFake(), CredentialRepositoryFake(),
            object : PasswordHasher {
                override fun hash(raw: Secret) = PasswordHash(Secret("unused"))
                override fun verify(raw: Secret, hash: PasswordHash) = false
            },
            challenges, TransactionRunnerFake(),
        )

        reset.reset(ResetPasswordRequest(challenge.id, email, code, Secret("a new memorable passphrase"))) shouldBe true
    }

    "a registration challenge cannot reset a password" {
        val challenges = challenges()
        val challenge = challenges.issue(email, EmailChallengePurpose.REGISTRATION)!!
        val reset = ResetPasswordUseCase.Replace(
            UserRepositoryFake(), CredentialRepositoryFake(),
            object : PasswordHasher {
                override fun hash(raw: Secret) = PasswordHash(Secret("unused"))
                override fun verify(raw: Secret, hash: PasswordHash) = false
            },
            challenges, TransactionRunnerFake(),
        )

        reset.reset(ResetPasswordRequest(challenge.id, email, code, Secret("a new memorable passphrase"))) shouldBe false
        challenges.verify(challenge.id, email, EmailChallengePurpose.REGISTRATION, code) shouldBe true
    }
})

private class ResetStore : EmailChallengeStore {
    private val records = mutableMapOf<Uuid, Pair<EmailChallenge, Secret>>()
    private val attempts = mutableMapOf<Uuid, Int>()
    private val consumed = mutableSetOf<Uuid>()
    override suspend fun issue(challenge: EmailChallenge, hash: Secret, now: Instant, resendAt: Instant, maxAttempts: Int): Boolean {
        records[challenge.id] = challenge to hash
        attempts[challenge.id] = maxAttempts
        return true
    }
    override suspend fun find(id: Uuid): EmailChallenge? = records[id]?.first
    override suspend fun consume(id: Uuid, hash: Secret, now: Instant): Boolean {
        val (challenge, expected) = records[id] ?: return false
        if (id in consumed || challenge.expiresAt <= now || (attempts[id] ?: 0) <= 0) return false
        attempts[id] = attempts.getValue(id) - 1
        return (expected == hash).also { if (it) consumed += id }
    }
    override suspend fun revoke(id: Uuid) { consumed += id }
}

private class ResetCodes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("backup")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}
