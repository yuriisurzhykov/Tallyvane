package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.Credential
import tallyvane.identity.domain.Email
import tallyvane.identity.domain.RegisterOutcome
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant

class RegisterSpec :
    StringSpec({
        fun request(email: String = "person@example.com") = RegisterWithPasswordRequest(
            email = Email(email),
            rawPassword = Secret("correct horse battery staple"),
            displayName = null,
        )

        fun register(
            users: UserRepositoryFake = UserRepositoryFake(),
            credentials: CredentialRepositoryFake = CredentialRepositoryFake(),
        ) = RegisterWithPasswordUseCase.Register(
            users = users,
            credentials = credentials,
            passwordHasher = PasswordHasherFake(),
            transactions = TransactionRunnerFake(),
            ids = IdGeneratorFake(),
            clock = ClockFake(Instant.parse("2026-01-01T00:00:00Z")),
        )

        "registers a new account and returns its user id" {
            val outcome = register().register(request())

            outcome.shouldBeInstanceOf<RegisterOutcome.Registered>()
        }

        "saves a password credential for the new user" {
            val credentials = CredentialRepositoryFake()
            val outcome = register(credentials = credentials).register(request())

            val userId = (outcome as RegisterOutcome.Registered).userId
            credentials.findPasswordFor(userId).shouldBeInstanceOf<Credential.PasswordRecord>()
        }

        "refuses a second registration for the same email" {
            val users = UserRepositoryFake()
            val useCase = register(users = users)
            useCase.register(request())

            val second = useCase.register(request())

            second shouldBe RegisterOutcome.EmailTaken
        }

        "the original account's credential survives a rejected duplicate registration" {
            val credentials = CredentialRepositoryFake()
            val useCase = register(credentials = credentials)
            val first = useCase.register(request()) as RegisterOutcome.Registered

            useCase.register(request())

            credentials.findPasswordFor(first.userId).shouldBeInstanceOf<Credential.PasswordRecord>()
        }
    })
