package tallyvane.identity.application.port

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.user.UserId

/**
 * The behaviour every [SecondFactorMethod] must show, whatever mechanism it wraps.
 *
 * Written once and inherited by each implementation's own spec: the fake in this module's own
 * `src/test`, and [SecondFactorMethod.Rfc6238] in `src/test` too (ADR-046). [correctCodeFor] is
 * the one hook a real mechanism and a test fake cannot share: a fake's correct code is whatever
 * constant it was built with, [SecondFactorMethod.Rfc6238]'s is computed from the enrollment
 * payload it just handed back, and neither can stand in for the other here.
 */
public abstract class SecondFactorMethodConformance : StringSpec() {
    protected abstract suspend fun fresh(): SecondFactorMethod

    protected abstract fun userId(): UserId

    protected abstract fun correctCodeFor(startEnrollmentPayload: String): String

    init {
        "a user with no enrollment at all is not enrolled, and verify refuses" {
            val method = fresh()

            method.isEnrolledFor(userId()) shouldBe false
            method.verify(userId(), IMPLAUSIBLE_CODE) shouldBe false
        }

        "starting enrollment does not activate it" {
            val method = fresh()

            method.startEnrollment(userId())

            method.isEnrolledFor(userId()) shouldBe false
        }

        "confirming with the wrong code refuses, and enrollment stays inactive" {
            val method = fresh()
            method.startEnrollment(userId())

            val confirmed = method.confirmEnrollment(userId(), IMPLAUSIBLE_CODE)

            confirmed shouldBe false
            method.isEnrolledFor(userId()) shouldBe false
        }

        "confirming with the correct code activates enrollment, and verify then accepts it" {
            val method = fresh()
            val payload = method.startEnrollment(userId())
            val code = correctCodeFor(payload)

            val confirmed = method.confirmEnrollment(userId(), code)

            confirmed shouldBe true
            method.isEnrolledFor(userId()) shouldBe true
            method.verify(userId(), code) shouldBe true
        }

        "once active, verify refuses a code that does not match" {
            val method = fresh()
            val payload = method.startEnrollment(userId())
            method.confirmEnrollment(userId(), correctCodeFor(payload))

            method.verify(userId(), IMPLAUSIBLE_CODE) shouldBe false
        }
    }

    private companion object {
        /**
         * Never a real fake's configured code, and never a genuine six-digit TOTP value —
         * deliberately shaped so no implementation could accept it by coincidence.
         */
        const val IMPLAUSIBLE_CODE = "not-a-real-code"
    }
}
