package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TotpEnrollmentStoreFake
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.AuthenticationRule
import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.identity.domain.secondfactor.MfaRequirement
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DisableSpec :
    StringSpec({
        "disables an enrolled factor after recent reauthentication and explicit confirmation" {
            val fixture = Fixture()
            fixture.enrollTotp()

            fixture.useCase.disable(fixture.request()) shouldBe DisableSecondFactorUseCase.Outcome.DISABLED
            fixture.totp.find(fixture.userId) shouldBe null
        }

        "requires a fresh proof within five minutes before disabling a factor" {
            val fixture = Fixture(reauthenticatedAt = Fixture.now - 6.minutes)
            fixture.enrollTotp()

            fixture.useCase.disable(fixture.request()) shouldBe
                DisableSecondFactorUseCase.Outcome.REAUTHENTICATION_REQUIRED
            fixture.totp.find(fixture.userId) shouldBe fixture.enrollment
        }

        "keeps the only factor allowed by a required sign-in scheme" {
            val fixture = Fixture(policy = passwordRequiresTotp())
            fixture.enrollTotp()

            fixture.useCase.disable(fixture.request()) shouldBe DisableSecondFactorUseCase.Outcome.REQUIRED_BY_POLICY
            fixture.totp.find(fixture.userId) shouldBe fixture.enrollment
        }

        "requires explicit confirmation before changing enrollment" {
            val fixture = Fixture()
            fixture.enrollTotp()

            fixture.useCase.disable(fixture.request(confirmed = false)) shouldBe
                DisableSecondFactorUseCase.Outcome.CONFIRMATION_REQUIRED
            fixture.totp.find(fixture.userId) shouldBe fixture.enrollment
        }
    })

private class Fixture(
    private val reauthenticatedAt: Instant = now - 1.minutes,
    policy: AuthenticationPolicy = AuthenticationPolicy.defaults(),
) {
    val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000101"))
    private val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000102"))
    val totp = TotpEnrollmentStoreFake()
    val enrollment = TotpEnrollment(userId, EncryptedSecret("secret"), active = true, createdAt = now)
    private val sessions = SessionStoreFake()
    val useCase: DisableSecondFactorUseCase = DisableSecondFactorUseCase.Disable(
        sessions = sessions,
        totp = totp,
        emailMfa = EmailFactors(),
        backupCodes = BackupFactors(),
        policies = Policies(policy),
        clock = ClockFake(now),
        transactions = TransactionRunnerFake(),
    )

    init {
        sessions.saved[sessionId] = Session(
            id = sessionId,
            userId = userId,
            device = DeviceLabel("Browser"),
            tokenFamilyId = TokenFamilyId(Uuid.parse("00000000-0000-7000-8000-000000000103")),
            createdAt = now - 1.hours,
            lastUsedAt = now,
            revokedAt = null,
            reauthenticatedAt = reauthenticatedAt,
        )
    }

    suspend fun enrollTotp() {
        totp.save(enrollment)
    }

    fun request(confirmed: Boolean = true) = DisableSecondFactorUseCase.Request(
        userId = userId,
        sessionId = sessionId,
        kind = SecondFactorKind.TOTP,
        confirmed = confirmed,
    )

    companion object {
        val now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}

private class EmailFactors : EmailMfaEnrollmentStore {
    private val enrolled = mutableSetOf<UserId>()
    override suspend fun enroll(userId: UserId) {
        enrolled += userId
    }
    override suspend fun unenroll(userId: UserId) {
        enrolled -= userId
    }
    override suspend fun isEnrolled(userId: UserId): Boolean = userId in enrolled
}

private class BackupFactors : BackupCodeStore {
    private val enrolled = mutableSetOf<UserId>()
    override suspend fun replace(userId: UserId, hashes: List<Secret>) {
        if (hashes.isEmpty()) enrolled -= userId else enrolled += userId
    }
    override suspend fun consume(userId: UserId, hash: Secret): Boolean = false
    override suspend fun hasAny(userId: UserId): Boolean = userId in enrolled
}

private class Policies(private val policy: AuthenticationPolicy) : AuthenticationPolicyStore {
    override suspend fun current(): AuthenticationPolicy = policy
    override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy): Boolean = false
}

private fun passwordRequiresTotp(): AuthenticationPolicy = AuthenticationPolicy(
    version = 3,
    rules = AuthenticationPolicy.defaults().rules.values.map { rule ->
        if (rule.primary == PrimaryMethod.PASSWORD) {
            AuthenticationRule(
                rule.primary,
                rule.enabled,
                MfaRequirement.REQUIRED,
                setOf(SecondFactorKind.TOTP),
            )
        } else {
            rule
        }
    },
    advancedAcknowledged = false,
)
