package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.uuid.Uuid

class ConfirmSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))

        "a correct code dispatched to the right method activates it" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP, correctCode = "654321")
            val confirm =
                ConfirmSecondFactorEnrollmentUseCase.Confirm(
                    SecondFactorMethodRegistry.Default(listOf(totp)),
                    TransactionRunnerFake(),
                )

            val result = confirm.confirm(ConfirmSecondFactorEnrollmentRequest(userId, SecondFactorKind.TOTP, "654321"))

            result shouldBe true
            totp.isEnrolledFor(userId) shouldBe true
        }

        "a wrong code refuses without activating anything" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP, correctCode = "654321")
            val confirm =
                ConfirmSecondFactorEnrollmentUseCase.Confirm(
                    SecondFactorMethodRegistry.Default(listOf(totp)),
                    TransactionRunnerFake(),
                )

            val result = confirm.confirm(ConfirmSecondFactorEnrollmentRequest(userId, SecondFactorKind.TOTP, "wrong"))

            result shouldBe false
            totp.isEnrolledFor(userId) shouldBe false
        }

        "a kind nothing is registered for refuses, not throws" {
            val confirm =
                ConfirmSecondFactorEnrollmentUseCase.Confirm(
                    SecondFactorMethodRegistry.Default(emptyList()),
                    TransactionRunnerFake(),
                )

            val result = confirm.confirm(ConfirmSecondFactorEnrollmentRequest(userId, SecondFactorKind.TOTP, "654321"))

            result shouldBe false
        }
    })
