package tallyvane.identity.application.password

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.AuthenticationCompleter
import tallyvane.identity.application.SessionIssuer
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SignInSpec :
    StringSpec({
        val correctPassword = Secret("correct horse battery staple")
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val user = User(
            id = userId,
            email = Email("person@example.com"),
            displayName = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            disabledAt = null,
        )
        val device = DeviceLabel("Chrome on MacBook")

        fun sessionIssuer() = SessionIssuer.Default(
            sessions = SessionStoreFake(),
            refreshTokens = RefreshTokenStoreFake(),
            tokenFactory = TokenFactoryFake(),
            tokenHasher = TokenHasherFake(),
            clock = ClockFake(Instant.parse("2026-01-01T00:00:00Z")),
            ids = IdGeneratorFake(),
            accessTokenTtl = 15.minutes,
            refreshTokenIdleTtl = 30.days,
        )

        fun authenticationCompleter(enrolledMethods: List<SecondFactorMethodFake> = emptyList()) =
            AuthenticationCompleter.Default(
                registry = SecondFactorMethodRegistry.Default(enrolledMethods),
                pendingAuthentications = PendingAuthenticationStoreFake(),
                sessions = sessionIssuer(),
                ids = IdGeneratorFake(),
                clock = ClockFake(Instant.parse("2026-01-01T00:00:00Z")),
                pendingAuthenticationTtl = 5.minutes,
            )

        suspend fun withRegisteredUser(
            registered: User = user,
            hasPassword: Boolean = true,
            enrolledMethods: List<SecondFactorMethodFake> = emptyList(),
        ): SignInWithPasswordUseCase.SignIn {
            val hasher = PasswordHasherFake()
            val users = UserRepositoryFake().also { it.insert(registered) }
            val credentials = CredentialRepositoryFake()
            if (hasPassword) {
                credentials.save(registered.id, Credential.PasswordRecord(hasher.hash(correctPassword)))
            }
            return SignInWithPasswordUseCase.SignIn(
                users,
                credentials,
                hasher,
                authenticationCompleter(enrolledMethods),
                TransactionRunnerFake(),
            )
        }

        "a correct password issues a session" {
            val signIn = withRegisteredUser()

            val result = signIn.signIn(SignInWithPasswordRequest(user.email, correctPassword, device))

            result.shouldBeInstanceOf<SignInOutcome.Issued>()
        }

        "the issued session is recorded under the signed-in user's own id" {
            val signIn = withRegisteredUser()

            val result = signIn.signIn(SignInWithPasswordRequest(user.email, correctPassword, device))

            val issued = result.shouldBeInstanceOf<SignInOutcome.Issued>()
            issued.session.session.userId.value shouldBe userId.value
        }

        "a wrong password is rejected the same way as an unknown email" {
            val signIn = withRegisteredUser()

            val result = signIn.signIn(SignInWithPasswordRequest(user.email, Secret("wrong password"), device))

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
        }

        "an unknown email is rejected without revealing that it is unknown" {
            val signIn = withRegisteredUser()

            val result = signIn.signIn(SignInWithPasswordRequest(Email("nobody@example.com"), correctPassword, device))

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
        }

        "a disabled account is rejected as disabled, even with the correct password" {
            val signIn = withRegisteredUser(registered = user.copy(disabledAt = Instant.parse("2026-06-01T00:00:00Z")))

            val result = signIn.signIn(SignInWithPasswordRequest(user.email, correctPassword, device))

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.AccountDisabled)
        }

        "an account with no password credential is rejected as an invalid credential" {
            val signIn = withRegisteredUser(hasPassword = false)

            val result = signIn.signIn(SignInWithPasswordRequest(user.email, correctPassword, device))

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
        }

        "a correct password for a user with a second factor enrolled requires it, instead of issuing a session" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }
            val signIn = withRegisteredUser(enrolledMethods = listOf(totp))

            val result = signIn.signIn(SignInWithPasswordRequest(user.email, correctPassword, device))

            val outcome = result.shouldBeInstanceOf<SignInOutcome.NotIssued>()
            val reason = outcome.reason.shouldBeInstanceOf<AuthenticationOutcome.RequiresSecondFactor>()
            reason.availableMethods shouldBe setOf(SecondFactorKind.TOTP)
        }
    })
