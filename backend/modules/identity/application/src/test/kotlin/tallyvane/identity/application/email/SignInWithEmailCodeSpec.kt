package tallyvane.identity.application.email

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.AuthenticationCompleter
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SignInWithEmailCodeSpec :
    StringSpec({
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val accountId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000021"))
        val email = Email("person@example.test")
        val code = Secret("123456")

        fun challenges() = EmailChallenges(
            Store(),
            object : EmailDelivery {
                override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) = Unit
            },
            Codes(),
            TransactionRunnerFake(),
            IdGeneratorFake(),
            ClockFake(now),
        )

        "a verified email-login challenge completes the existing authentication policy" {
            val users = UserRepositoryFake()
            users.insert(User(accountId, email, null, now, null))
            val service = challenges()
            val challenge = service.issue(email, EmailChallengePurpose.EMAIL_LOGIN)!!
            val expected = SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
            var completed = false
            val completer = object : AuthenticationCompleter {
                override suspend fun complete(
                    userId: UserId,
                    device: DeviceLabel,
                    primaryMethod: tallyvane.identity.domain.secondfactor.PrimaryMethod,
                ): SignInOutcome {
                    completed = userId == accountId && device.value == "Browser"
                    return expected
                }
            }
            val signIn = SignInWithEmailCodeUseCase.SignIn(users, service, completer, TransactionRunnerFake())

            signIn.signIn(SignInWithEmailCodeRequest(challenge.id, email, code, DeviceLabel("Browser"))) shouldBe
                expected
            completed shouldBe true
            service.verify(challenge.id, email, EmailChallengePurpose.EMAIL_LOGIN, code) shouldBe false
        }

        "a different-purpose challenge cannot be spent to sign in" {
            val users = UserRepositoryFake()
            users.insert(User(accountId, email, null, now, null))
            val service = challenges()
            val challenge = service.issue(email, EmailChallengePurpose.PASSWORD_RESET)!!
            var completed = false
            val completer = object : AuthenticationCompleter {
                override suspend fun complete(
                    userId: UserId,
                    device: DeviceLabel,
                    primaryMethod: tallyvane.identity.domain.secondfactor.PrimaryMethod,
                ): SignInOutcome {
                    completed = true
                    return SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
                }
            }
            val signIn = SignInWithEmailCodeUseCase.SignIn(users, service, completer, TransactionRunnerFake())

            signIn.signIn(SignInWithEmailCodeRequest(challenge.id, email, code, DeviceLabel("Browser"))) shouldBe
                SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            completed shouldBe false
            service.verify(challenge.id, email, EmailChallengePurpose.PASSWORD_RESET, code) shouldBe true
        }
    })

private class Store : EmailChallengeStore {
    private val records = mutableMapOf<Uuid, Pair<EmailChallenge, Secret>>()
    private val attempts = mutableMapOf<Uuid, Int>()
    private val consumed = mutableSetOf<Uuid>()
    override suspend fun issue(
        challenge: EmailChallenge,
        hash: Secret,
        now: Instant,
        resendAt: Instant,
        maxAttempts: Int,
    ): Boolean {
        records[challenge.id] = challenge to hash
        attempts[challenge.id] = maxAttempts
        return true
    }
    override suspend fun find(id: Uuid): EmailChallenge? = records[id]?.first
    override suspend fun consume(id: Uuid, hash: Secret, now: Instant): Boolean =
        records[id]?.takeIf { (challenge, _) ->
            id !in consumed && challenge.expiresAt > now && (attempts[id] ?: 0) > 0
        }?.let { (_, expected) ->
            attempts[id] = attempts.getValue(id) - 1
            (expected == hash).also { if (it) consumed += id }
        } ?: false
    override suspend fun revoke(id: Uuid) {
        consumed += id
    }
}

private class Codes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("backup")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}
