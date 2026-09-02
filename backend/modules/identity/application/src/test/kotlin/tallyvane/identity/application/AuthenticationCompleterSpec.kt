package tallyvane.identity.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.secondfactor.SecondFactorMethodRegistry
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AuthenticationCompleterSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val device = DeviceLabel("Chrome on MacBook")
        val now = Instant.parse("2026-01-01T00:00:00Z")

        fun completer(
            methods: List<SecondFactorMethodFake> = emptyList(),
            pending: PendingAuthenticationStoreFake = PendingAuthenticationStoreFake(),
        ) = AuthenticationCompleter.Default(
            registry = SecondFactorMethodRegistry.Default(methods),
            pendingAuthentications = pending,
            sessions = SessionIssuer.Default(
                sessions = SessionStoreFake(),
                tokenFactory = TokenFactoryFake(),
                transactions = TransactionRunnerFake(),
                clock = ClockFake(now),
                ids = IdGeneratorFake(),
            ),
            ids = IdGeneratorFake(),
            clock = ClockFake(now),
            pendingAuthenticationTtl = 5.minutes,
        )

        "a user with no second factor enrolled is issued a session directly" {
            val result = completer().complete(userId, device)

            val issued = result.shouldBeInstanceOf<SignInOutcome.Issued>()
            issued.session.session.userId shouldBe userId
        }

        "a user with a second factor enrolled is not issued a session — a pending authentication is created instead" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }
            val pending = PendingAuthenticationStoreFake()

            val result = completer(listOf(totp), pending).complete(userId, device)

            val outcome = result.shouldBeInstanceOf<SignInOutcome.NotIssued>()
            val reason = outcome.reason.shouldBeInstanceOf<AuthenticationOutcome.RequiresSecondFactor>()
            reason.availableMethods shouldBe setOf(SecondFactorKind.TOTP)
            pending.saved[reason.pendingId]?.userId shouldBe userId
        }

        "the pending authentication carries the same device the primary sign-in presented" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }
            val pending = PendingAuthenticationStoreFake()

            val result = completer(listOf(totp), pending).complete(userId, device)

            val reason =
                (result as SignInOutcome.NotIssued).reason as AuthenticationOutcome.RequiresSecondFactor
            pending.saved[reason.pendingId]?.device shouldBe device
        }
    })
