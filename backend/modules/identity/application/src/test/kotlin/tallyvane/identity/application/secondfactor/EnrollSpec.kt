package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId
import kotlin.uuid.Uuid

class EnrollSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))

        "dispatches to the registered method for the requested kind" {
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP)
            val enroll = EnrollSecondFactorUseCase.Enroll(SecondFactorMethodRegistry.Default(listOf(totp)))

            val payload = enroll.enroll(EnrollSecondFactorRequest(userId, SecondFactorKind.TOTP))

            payload.shouldNotBeNull()
            totp.enrollmentStarted shouldBe userId
        }

        "answers null for a kind nothing is registered for, rather than throwing" {
            val enroll = EnrollSecondFactorUseCase.Enroll(SecondFactorMethodRegistry.Default(emptyList()))

            val payload = enroll.enroll(EnrollSecondFactorRequest(userId, SecondFactorKind.TOTP))

            payload.shouldBeNull()
        }
    })
