package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.password.RegisterWithPasswordRequest
import tallyvane.identity.application.password.SignInWithPasswordRequest
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.LoginAttemptsFake
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.outcome.RegisterOutcome
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class IdentityUseCasesSpec : StringSpec({
    "composition shares registered credentials and issues a refreshable session" {
        val clock = ClockFake(Instant.parse("2026-01-01T00:00:00Z"))
        val users = UserRepositoryFake()
        val cases = IdentityUseCases(
            users = users, credentials = CredentialRepositoryFake(),
            passwords = PasswordHasherFake(), sessions = SessionStoreFake(),
            refreshTokens = RefreshTokenStoreFake(), pending = PendingAuthenticationStoreFake(),
            factors = emptyList(), attempts = LoginAttemptsFake(), tokens = TokenFactoryFake(),
            hashes = TokenHasherFake(), transactions = TransactionRunnerFake(), clock = clock,
            ids = IdGeneratorFake(), accessTtl = 15.minutes, refreshIdleTtl = 30.days,
            pendingTtl = 5.minutes, attemptLimit = 5, attemptWindow = 15.minutes,
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
