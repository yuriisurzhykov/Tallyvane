package tallyvane.identity.application.password

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.Secret
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

        suspend fun withRegisteredUser(
            registered: User = user,
            hasPassword: Boolean = true,
        ): SignInWithPasswordUseCase.SignIn {
            val hasher = PasswordHasherFake()
            val users = UserRepositoryFake().also { it.insert(registered) }
            val credentials = CredentialRepositoryFake()
            if (hasPassword) {
                credentials.save(registered.id, Credential.PasswordRecord(hasher.hash(correctPassword)))
            }
            return SignInWithPasswordUseCase.SignIn(users, credentials, hasher)
        }

        "a correct password succeeds, naming the user id" {
            val signIn = withRegisteredUser()

            val outcome = signIn.signIn(SignInWithPasswordRequest(user.email, correctPassword))

            outcome shouldBe AuthenticationOutcome.Success(userId)
        }

        "a wrong password is rejected the same way as an unknown email" {
            val signIn = withRegisteredUser()

            val outcome = signIn.signIn(SignInWithPasswordRequest(user.email, Secret("wrong password")))

            outcome shouldBe AuthenticationOutcome.InvalidCredential
        }

        "an unknown email is rejected without revealing that it is unknown" {
            val signIn = withRegisteredUser()

            val outcome = signIn.signIn(SignInWithPasswordRequest(Email("nobody@example.com"), correctPassword))

            outcome shouldBe AuthenticationOutcome.InvalidCredential
        }

        "a disabled account is rejected as disabled, even with the correct password" {
            val signIn = withRegisteredUser(registered = user.copy(disabledAt = Instant.parse("2026-06-01T00:00:00Z")))

            val outcome = signIn.signIn(SignInWithPasswordRequest(user.email, correctPassword))

            outcome shouldBe AuthenticationOutcome.AccountDisabled
        }

        "an account with no password credential is rejected as an invalid credential" {
            val signIn = withRegisteredUser(hasPassword = false)

            val outcome = signIn.signIn(SignInWithPasswordRequest(user.email, correctPassword))

            outcome shouldBe AuthenticationOutcome.InvalidCredential
        }
    })
