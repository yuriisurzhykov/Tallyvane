package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.password.RegisterWithPasswordRequest
import tallyvane.identity.application.password.SignInWithPasswordRequest
import tallyvane.identity.application.port.AuthenticationPolicyAuditStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.BackupCodeStore
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.application.port.LoginAttemptsFake
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.outcome.RegisterOutcome
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class IdentityUseCasesSpec :
    StringSpec({
        "composition shares registered credentials and issues a refreshable session" {
            val clock = ClockFake(Instant.parse("2026-01-01T00:00:00Z"))
            val users = UserRepositoryFake()
            val stores = IdentityUseCasesStoresFake()
            val cases = IdentityUseCases(
                users = users, credentials = CredentialRepositoryFake(),
                passwords = PasswordHasherFake(), sessions = SessionStoreFake(),
                refreshTokens = RefreshTokenStoreFake(), pending = PendingAuthenticationStoreFake(),
                factors = emptyList(), attempts = LoginAttemptsFake(), tokens = TokenFactoryFake(),
                hashes = TokenHasherFake(), transactions = TransactionRunnerFake(), clock = clock,
                ids = IdGeneratorFake(), accessTtl = 15.minutes, refreshIdleTtl = 30.days,
                pendingTtl = 5.minutes, attemptLimit = 5, attemptWindow = 15.minutes,
                emailMfaEnrollmentStore = stores,
                authenticationPolicyStore = stores,
                authenticationPolicyAuditStore = stores,
                totpEnrollmentStore = stores,
                backupCodeStore = stores,
            )
            val email = Email("owner@example.com")
            val password = Secret("a quiet evening at home")
            val registered = cases.register.register(RegisterWithPasswordRequest(email, password, null))
                .shouldBeInstanceOf<RegisterOutcome.Registered>()
            // This composition fixture skips email delivery, so model the state after verification.
            users.markEmailVerified(registered.userId)
            val issued = cases.signIn.signIn(SignInWithPasswordRequest(email, password, DeviceLabel("Test browser")))
                .shouldBeInstanceOf<SignInOutcome.Issued>()
            cases.refresh.refresh(issued.session.tokens.refresh)
                .shouldBeInstanceOf<tallyvane.identity.application.session.RefreshSessionOutcome.Issued>()
        }
    })

private class IdentityUseCasesStoresFake :
    AuthenticationPolicyStore,
    AuthenticationPolicyAuditStore,
    EmailMfaEnrollmentStore,
    TotpEnrollmentStore,
    BackupCodeStore {
    private var policy = AuthenticationPolicy.defaults()

    override suspend fun current(): AuthenticationPolicy = policy

    override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy): Boolean {
        if (this.policy.version != expectedVersion) return false
        this.policy = policy
        return true
    }

    override suspend fun record(actor: UserId, action: String, policyVersion: Long, occurredAt: Instant) = Unit

    override suspend fun enroll(userId: UserId) = Unit

    override suspend fun unenroll(userId: UserId) = Unit

    override suspend fun isEnrolled(userId: UserId): Boolean = false

    override suspend fun save(enrollment: TotpEnrollment) = Unit

    override suspend fun find(userId: UserId): TotpEnrollment? = null

    override suspend fun delete(userId: UserId) = Unit

    override suspend fun replace(userId: UserId, hashes: List<Secret>) = Unit

    override suspend fun consume(userId: UserId, hash: Secret): Boolean = false

    override suspend fun hasAny(userId: UserId): Boolean = false
}
