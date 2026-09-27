package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TotpEnrollmentStoreFake
import tallyvane.identity.application.port.VALID_ACTION_PROOF
import tallyvane.identity.application.port.acceptingActionProofRequirement
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
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DisableSpec :
    StringSpec({
        "disables an enrolled factor after an action proof and explicit confirmation" {
            val fixture = DisableFixture()
            fixture.enrollTotp()

            fixture.useCase.disable(fixture.request()) shouldBe DisableSecondFactorUseCase.Outcome.DISABLED
            fixture.totp.find(fixture.userId) shouldBe null
        }

        "requires an action proof before disabling a factor" {
            val fixture = DisableFixture()
            fixture.enrollTotp()

            fixture.useCase.disable(fixture.request(actionProof = null)) shouldBe
                DisableSecondFactorUseCase.Outcome.REAUTHENTICATION_REQUIRED
            fixture.totp.find(fixture.userId) shouldBe fixture.enrollment
        }

        "keeps the only factor allowed by a required sign-in scheme" {
            val fixture = DisableFixture(policy = passwordRequiresTotp())
            fixture.enrollTotp()
            fixture.enrollEmail()

            fixture.useCase.disable(fixture.request()) shouldBe DisableSecondFactorUseCase.Outcome.REQUIRED_BY_POLICY
            fixture.totp.find(fixture.userId) shouldBe fixture.enrollment
        }

        "requires explicit confirmation before changing enrollment" {
            val fixture = DisableFixture()
            fixture.enrollTotp()

            fixture.useCase.disable(fixture.request(confirmed = false)) shouldBe
                DisableSecondFactorUseCase.Outcome.CONFIRMATION_REQUIRED
            fixture.totp.find(fixture.userId) shouldBe fixture.enrollment
        }
    })

private class DisableFixture(policy: AuthenticationPolicy = AuthenticationPolicy.defaults()) {
    val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000101"))
    private val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000102"))
    val totp = TotpEnrollmentStoreFake()
    private val email = EmailFactors()
    val enrollment = TotpEnrollment(userId, EncryptedSecret("secret"), active = true, createdAt = now)
    private val sessions = SessionStoreFake()
    val useCase: DisableSecondFactorUseCase = DisableSecondFactorUseCase.Disable(
        sessions = sessions,
        totp = totp,
        emailMfa = email,
        policies = Policies(policy),
        clock = ClockFake(now),
        transactions = TransactionRunnerFake(),
        actionProofs = acceptingActionProofRequirement(userId, sessionId, now),
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
            reauthenticatedAt = null,
        )
    }

    suspend fun enrollTotp() {
        totp.save(enrollment)
    }

    suspend fun enrollEmail() {
        email.enroll(userId)
    }

    fun request(confirmed: Boolean = true, actionProof: String? = VALID_ACTION_PROOF) =
        DisableSecondFactorUseCase.Request(
            userId = userId,
            sessionId = sessionId,
            kind = SecondFactorKind.TOTP,
            confirmed = confirmed,
            actionProof = actionProof,
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
