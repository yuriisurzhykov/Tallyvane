package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.port.AuthenticationCodes
import tallyvane.identity.application.port.EmailChallengeStore
import tallyvane.identity.application.port.EmailDelivery
import tallyvane.identity.application.port.PendingAuthenticationStore
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.email.EmailChallenge
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
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

class RequestSpec :
    StringSpec({
        "requests an email MFA challenge bound to an active eligible pending sign-in" {
            val fixture = RequestFixture()
            fixture.install()
            val challengeId = fixture.subject.request(fixture.pendingId)

            challengeId shouldBe fixture.store.lastIssued?.id
            fixture.delivery.sent shouldHaveSize 1
            fixture.store.lastIssued?.purpose shouldBe EmailChallengePurpose.MFA
            fixture.store.lastIssued?.binding shouldBe fixture.pendingId.value.toString()
        }

        "refuses an expired email MFA pending sign-in without sending a code" {
            val fixture = RequestFixture(pendingExpiresAt = NOW)
            fixture.install()

            fixture.subject.request(fixture.pendingId) shouldBe null
            fixture.delivery.sent shouldHaveSize 0
        }
    })

private class RequestFixture(pendingExpiresAt: Instant = NOW + kotlin.time.Duration.parse("5m")) {
    val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
    val pendingId = PendingAuthenticationId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
    val delivery = RequestDelivery()
    val store = RequestChallengeStore()
    private val users = UserRepositoryFake()
    private val pending = object : PendingAuthenticationStore {
        override suspend fun save(pending: PendingAuthentication) = Unit
        override suspend fun find(id: PendingAuthenticationId): PendingAuthentication? = if (id != pendingId) {
            null
        } else {
            PendingAuthentication(
                pendingId,
                userId,
                DeviceLabel("Browser"),
                setOf(SecondFactorKind.EMAIL_OTP),
                NOW,
                pendingExpiresAt,
            )
        }
        override suspend fun delete(id: PendingAuthenticationId) = Unit
        override suspend fun deleteFor(userId: UserId) = Unit
    }
    private val challenges = EmailChallenges(
        store,
        delivery,
        RequestCodes(),
        TransactionRunnerFake(),
        IdGeneratorFake(),
        ClockFake(NOW),
    )
    val subject = RequestEmailMfaCodeUseCase.Request(
        pending,
        users,
        challenges,
        ClockFake(NOW),
        TransactionRunnerFake(),
    )

    suspend fun install() {
        users.insert(User(userId, Email("person@example.test"), null, NOW, null, emailVerified = true))
    }
}

private class RequestDelivery : EmailDelivery {
    val sent = mutableListOf<EmailChallengePurpose>()
    override suspend fun sendCode(email: Email, purpose: EmailChallengePurpose, code: Secret) {
        sent += purpose
    }
}

private class RequestChallengeStore : EmailChallengeStore {
    var lastIssued: EmailChallenge? = null
    override suspend fun issue(
        challenge: EmailChallenge,
        hash: Secret,
        now: Instant,
        resendAt: Instant,
        maxAttempts: Int,
    ): Boolean {
        lastIssued = challenge
        return true
    }
    override suspend fun find(id: Uuid): EmailChallenge? = lastIssued?.takeIf { it.id == id }
    override suspend fun consume(id: Uuid, hash: Secret, now: Instant): Boolean = false
    override suspend fun revoke(id: Uuid) {
        if (lastIssued?.id == id) lastIssued = null
    }
}

private class RequestCodes : AuthenticationCodes {
    override fun emailCode() = Secret("123456")
    override fun backupCode() = Secret("backup")
    override fun hash(context: String, code: Secret) = Secret("$context:${code.revealed()}")
}

private val NOW = Instant.parse("2026-01-01T00:00:00Z")
