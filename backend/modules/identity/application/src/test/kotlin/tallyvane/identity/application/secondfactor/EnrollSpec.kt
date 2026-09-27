package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.SecondFactorMethodFake
import tallyvane.identity.application.port.VALID_ACTION_PROOF
import tallyvane.identity.application.port.acceptingActionProofRequirement
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EnrollSpec :
    StringSpec({
        val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000001"))

        "dispatches to the registered method for the requested kind" {
            val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val totp = SecondFactorMethodFake(SecondFactorKind.TOTP)
            val enroll = EnrollSecondFactorUseCase.Enroll(
                SecondFactorMethodRegistry.Default(listOf(totp)),
                TransactionRunnerFake(),
                acceptingActionProofRequirement(userId, sessionId, now),
            )

            val payload = enroll.enroll(
                EnrollSecondFactorRequest(userId, SecondFactorKind.TOTP, sessionId, VALID_ACTION_PROOF),
            )

            payload.shouldNotBeNull()
            totp.enrollmentStarted shouldBe userId
        }

        "answers null for a kind nothing is registered for, rather than throwing" {
            val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000002"))
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val enroll = EnrollSecondFactorUseCase.Enroll(
                SecondFactorMethodRegistry.Default(emptyList()),
                TransactionRunnerFake(),
                acceptingActionProofRequirement(userId, sessionId, now),
            )

            val payload = enroll.enroll(
                EnrollSecondFactorRequest(userId, SecondFactorKind.TOTP, sessionId, VALID_ACTION_PROOF),
            )

            payload.shouldBeNull()
        }
    })
