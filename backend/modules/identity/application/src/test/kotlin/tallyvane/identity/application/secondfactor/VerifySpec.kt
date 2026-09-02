package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.identity.application.SessionIssuer
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.domain.outcome.SecondFactorOutcome
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class VerifySpec :
    StringSpec({
        val pendingId = PendingAuthenticationId(Uuid.parse("00000000-0000-7000-8000-000000000001"))
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
        val device = DeviceLabel("Chrome on MacBook")
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val pending = PendingAuthentication(
            id = pendingId,
            userId = userId,
            device = device,
            availableMethods = setOf(SecondFactorKind.TOTP),
            createdAt = now,
            expiresAt = now + 5.minutes,
        )

        fun verify(store: PendingAuthenticationStoreFake, totp: SecondFactorMethodFake, clockAt: Instant = now) =
            VerifySecondFactorUseCase.Verify(
                pendingAuthentications = store,
                registry = SecondFactorMethodRegistry.Default(listOf(totp)),
                sessions = SessionIssuer.Default(
                    sessions = SessionStoreFake(),
                    tokenFactory = TokenFactoryFake(),
                    transactions = TransactionRunnerFake(),
                    clock = ClockFake(clockAt),
                    ids = IdGeneratorFake(),
                ),
                clock = ClockFake(clockAt),
            )

        "an unknown pending id is refused, distinct from a wrong code" {
            val store = PendingAuthenticationStoreFake()
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }

            val result = verify(
                store,
                totp,
            ).verify(VerifySecondFactorRequest(pendingId, SecondFactorKind.TOTP, "123456"))

            result shouldBe VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.UnknownPending)
        }

        "an expired pending authentication is refused and removed, so it cannot be retried" {
            val store = PendingAuthenticationStoreFake().also { it.save(pending) }
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }
            val afterExpiry = pending.expiresAt + 1.minutes

            val result =
                verify(
                    store,
                    totp,
                    afterExpiry,
                ).verify(VerifySecondFactorRequest(pendingId, SecondFactorKind.TOTP, "123456"))

            result shouldBe VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.Expired)
            store.find(pendingId).shouldBeNull()
        }

        "a correct code issues a session and removes the pending authentication" {
            val store = PendingAuthenticationStoreFake().also { it.save(pending) }
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }

            val result = verify(
                store,
                totp,
            ).verify(VerifySecondFactorRequest(pendingId, SecondFactorKind.TOTP, "123456"))

            val issued = result.shouldBeInstanceOf<VerifySecondFactorOutcome.Issued>()
            issued.session.session.userId shouldBe userId
            store.find(pendingId).shouldBeNull()
        }

        "the issued session keeps the device the primary sign-in presented, not one resent on this request" {
            val store = PendingAuthenticationStoreFake().also { it.save(pending) }
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }

            val result = verify(
                store,
                totp,
            ).verify(VerifySecondFactorRequest(pendingId, SecondFactorKind.TOTP, "123456"))

            val issued = result.shouldBeInstanceOf<VerifySecondFactorOutcome.Issued>()
            issued.session.session.device shouldBe device
        }

        "a wrong code is refused, and the pending authentication survives for a retry" {
            val store = PendingAuthenticationStoreFake().also { it.save(pending) }
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }

            val result = verify(
                store,
                totp,
            ).verify(VerifySecondFactorRequest(pendingId, SecondFactorKind.TOTP, "wrong"))

            result shouldBe VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.WrongCode)
            store.find(pendingId).shouldNotBeNull()
        }

        "a kind the registry has nothing registered for is refused, not thrown" {
            val store = PendingAuthenticationStoreFake().also { it.save(pending) }
            val empty = VerifySecondFactorUseCase.Verify(
                pendingAuthentications = store,
                registry = SecondFactorMethodRegistry.Default(emptyList()),
                sessions = SessionIssuer.Default(
                    sessions = SessionStoreFake(),
                    tokenFactory = TokenFactoryFake(),
                    transactions = TransactionRunnerFake(),
                    clock = ClockFake(now),
                    ids = IdGeneratorFake(),
                ),
                clock = ClockFake(now),
            )

            val result = empty.verify(VerifySecondFactorRequest(pendingId, SecondFactorKind.TOTP, "123456"))

            result shouldBe VerifySecondFactorOutcome.NotCompleted(SecondFactorOutcome.WrongCode)
        }
    })
