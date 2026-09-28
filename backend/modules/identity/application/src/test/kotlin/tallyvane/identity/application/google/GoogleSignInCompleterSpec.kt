package tallyvane.identity.application.google

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.AuthenticationCompleter
import tallyvane.identity.application.SessionIssuer
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.RefreshTokenStoreFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GoogleSignInCompleterSpec :
    StringSpec({
        val identity = GoogleIdentity(GoogleSubject("108234567890123456789"), Email("person@example.com"))
        val device = DeviceLabel("Chrome on MacBook")
        val now = Instant.parse("2026-01-01T00:00:00Z")

        fun authenticationCompleter() = AuthenticationCompleter.Default(
            registry = SecondFactorMethodRegistry.Default(emptyList()),
            pendingAuthentications = PendingAuthenticationStoreFake(),
            sessions = SessionIssuer.Default(
                sessions = SessionStoreFake(),
                refreshTokens = RefreshTokenStoreFake(),
                tokenFactory = TokenFactoryFake(),
                tokenHasher = TokenHasherFake(),
                clock = ClockFake(now),
                ids = IdGeneratorFake(),
                accessTokenTtl = 15.minutes,
                refreshTokenIdleTtl = 30.days,
            ),
            ids = IdGeneratorFake(),
            clock = ClockFake(now),
            pendingAuthenticationTtl = 5.minutes,
        )

        fun googleSignInCompleter(
            users: UserRepositoryFake = UserRepositoryFake(),
            credentials: CredentialRepositoryFake = CredentialRepositoryFake(),
        ) = GoogleSignInCompleter.Default(
            users = users,
            credentials = credentials,
            completer = authenticationCompleter(),
            transactions = TransactionRunnerFake(),
            ids = IdGeneratorFake(),
            clock = ClockFake(now),
        )

        "a first-time identity registers an account and issues a session" {
            val users = UserRepositoryFake()
            val credentials = CredentialRepositoryFake()

            val result = googleSignInCompleter(users, credentials).complete(identity, device)

            result.shouldBeInstanceOf<SignInOutcome.Issued>()
            users.findByEmail(identity.email).shouldNotBeNull()
            credentials.findUserIdByGoogleSubject(identity.subject).shouldNotBeNull()
        }

        "an already-linked identity signs the existing account in, without registering again" {
            val users = UserRepositoryFake()
            val credentials = CredentialRepositoryFake()
            val first = googleSignInCompleter(users, credentials).complete(identity, device) as SignInOutcome.Issued

            val second = googleSignInCompleter(users, credentials).complete(identity, device)

            second.shouldBeInstanceOf<SignInOutcome.Issued>()
            second.session.session.userId shouldBe first.session.session.userId
        }

        "an email already used by a different credential is refused, not silently linked" {
            val users = UserRepositoryFake()
            val credentials = CredentialRepositoryFake()
            val existingUserId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000099"))
            users.insert(
                User(
                    id = existingUserId,
                    email = identity.email,
                    displayName = null,
                    createdAt = now,
                    disabledAt = null,
                ),
            )

            val result = googleSignInCompleter(users, credentials).complete(identity, device)

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            credentials.findUserIdByGoogleSubject(identity.subject).shouldBeNull()
        }
    })
