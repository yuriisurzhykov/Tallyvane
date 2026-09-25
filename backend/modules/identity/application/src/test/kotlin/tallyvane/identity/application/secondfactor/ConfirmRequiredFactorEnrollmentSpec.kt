package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.PendingAuthenticationStoreFake
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.domain.secondfactor.PendingAuthentication
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConfirmRequiredFactorEnrollmentSpec :
    StringSpec({
        "activates the required factor and consumes its pending challenge" {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val userId = UserId(Uuid.random())
            val id = PendingAuthenticationId(Uuid.random())
            val pending = PendingAuthenticationStoreFake()
            pending.save(
                PendingAuthentication(
                    id, userId, DeviceLabel("Browser"), setOf(SecondFactorKind.TOTP), now,
                    now + 5.minutes, true, PrimaryMethod.PASSWORD, 3,
                ),
            )
            val factor = SecondFactorMethodFake(SecondFactorKind.TOTP)
            val useCase = ConfirmRequiredFactorEnrollmentUseCase.Confirm(
                pending,
                SecondFactorMethodRegistry.Default(listOf(factor)),
                ClockFake(now),
                TransactionRunnerFake(),
            )

            useCase.confirm(
                ConfirmRequiredFactorEnrollmentUseCase.Request(id, SecondFactorKind.TOTP, "123456"),
            ) shouldBe
                true
            factor.isEnrolledFor(userId) shouldBe true
            pending.find(id) shouldBe null
        }

        "does not allow MFA verification challenges to complete enrollment" {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val userId = UserId(Uuid.random())
            val id = PendingAuthenticationId(Uuid.random())
            val pending = PendingAuthenticationStoreFake()
            pending.save(
                PendingAuthentication(
                    id,
                    userId,
                    DeviceLabel("Browser"),
                    setOf(SecondFactorKind.TOTP),
                    now,
                    now + 5.minutes,
                ),
            )
            val factor = SecondFactorMethodFake(SecondFactorKind.TOTP).also { it.enroll(userId) }
            val useCase = ConfirmRequiredFactorEnrollmentUseCase.Confirm(
                pending,
                SecondFactorMethodRegistry.Default(listOf(factor)),
                ClockFake(now),
                TransactionRunnerFake(),
            )

            useCase.confirm(
                ConfirmRequiredFactorEnrollmentUseCase.Request(id, SecondFactorKind.TOTP, "123456"),
            ) shouldBe
                false
            pending.find(id) shouldBe pending.saved[id]
        }
    })
