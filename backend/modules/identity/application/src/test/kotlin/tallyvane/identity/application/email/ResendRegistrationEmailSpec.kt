package tallyvane.identity.application.email

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
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

class ResendRegistrationEmailSpec :
    StringSpec({
        "resends only for a matching unverified registration and does not distinguish unrelated accounts" {
            val users = UserRepositoryFake()
            val id = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
            val email = Email("new@example.test")
            users.insert(User(id, email, null, Instant.parse("2026-01-01T00:00:00Z"), null, false))
            val store = ResendChallengeStore()
            val challenges = EmailChallenges(
                store,
                object : EmailDelivery {
                    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) = Unit
                },
                ResendCodes(),
                TransactionRunnerFake(),
                IdGeneratorFake(),
                ClockFake(Instant.parse("2026-01-01T00:00:00Z")),
            )
            val resend = ResendRegistrationEmailUseCase.Send(users, challenges, TransactionRunnerFake())

            val issued = resend.resend(id, email)
            issued shouldBe store.lastIssued?.id
            store.lastIssued?.purpose shouldBe EmailChallengePurpose.REGISTRATION
            store.lastIssued?.binding shouldBe id.value.toString()

            resend.resend(id, Email("other@example.test")) shouldBe null
            resend.resend(UserId(Uuid.parse("00000000-0000-7000-8000-000000000002")), email) shouldBe null
            users.markEmailVerified(id)
            resend.resend(id, email) shouldBe null
            store.issueCount shouldBe 1
        }
    })

private class ResendChallengeStore : EmailChallengeStore {
    var lastIssued: EmailChallenge? = null
    var issueCount = 0
    override suspend fun issue(
        challenge: EmailChallenge,
        hash: Secret,
        now: Instant,
        resendAt: Instant,
        maxAttempts: Int,
    ): Boolean {
        issueCount++
        lastIssued = challenge
        return true
    }
    override suspend fun find(id: Uuid): EmailChallenge? = lastIssued?.takeIf { it.id == id }
    override suspend fun consume(id: Uuid, hash: Secret, now: Instant) = false
    override suspend fun revoke(id: Uuid) = Unit
}

private class ResendCodes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("backup")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}
