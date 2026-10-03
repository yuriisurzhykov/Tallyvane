package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.domain.TotpEnrollment

/**
 * The behaviour every [tallyvane.authentication.application.port.TotpEnrollments] must show, whatever keeps it.
 */
abstract class TotpEnrollmentsConformance : StringSpec() {
    protected abstract suspend fun fresh(): SecondFactorStorage

    init {
        "finds nothing for an account that has set nothing up" {
            val subject = fresh()

            subject.inOwnTransaction { subject.enrollments.find(ANN) } shouldBe null
            subject.inOwnTransaction { subject.enrollments.lock(ANN) } shouldBe null
            subject.inOwnTransaction { subject.codes.of(ANN) } shouldBe null
        }

        "finds an enrolment as it was kept, however it is read" {
            val subject = fresh()
            val begun = TotpEnrollment.begin(ENTROPY)
            subject.inOwnTransaction { subject.enrollments.keep(ANN, begun) }

            subject.inOwnTransaction { subject.enrollments.find(ANN) }?.told() shouldBe begun.told()
            subject.inOwnTransaction { subject.enrollments.lock(ANN) }?.told() shouldBe begun.told()
        }

        "keeping again replaces what the account had, and the step it remembers comes with it" {
            val subject = fresh()
            subject.inOwnTransaction { subject.enrollments.keep(ANN, TotpEnrollment.begin(ENTROPY)) }
            val active = activeAt(7)
            subject.inOwnTransaction { subject.enrollments.keep(ANN, active) }

            subject.inOwnTransaction { subject.enrollments.find(ANN) }?.told() shouldBe active.told()
        }

        "keeps each account's enrolment apart" {
            val subject = fresh()
            subject.inOwnTransaction { subject.enrollments.keep(ANN, activeAt(1)) }
            subject.inOwnTransaction { subject.enrollments.keep(BOB, activeAt(2)) }

            subject.inOwnTransaction { subject.enrollments.find(BOB) }?.told() shouldBe activeAt(2).told()
        }
    }
}
