package tallyvane.authentication.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.SpendVerdict

/**
 * The behaviour every [tallyvane.authentication.application.port.RecoveryCodeSets] must show, whatever keeps it.
 */
abstract class RecoveryCodeSetsConformance : StringSpec() {
    protected abstract suspend fun fresh(): SecondFactorStorage

    init {
        "finds the recovery codes as they were kept, spent ones included" {
            val subject = fresh()
            subject.inOwnTransaction { subject.enrollments.keep(ANN, activeAt(1)) }
            val issued = RecoveryCodes.issue((1..3).map(::digest))
            val spent = issued.spend(digest(2), AT).reportTo(
                object : SpendVerdict.Report<RecoveryCodes> {
                    override fun spent(next: RecoveryCodes): RecoveryCodes = next

                    override fun unknown(): RecoveryCodes = error("The code should have been spent.")
                },
            )
            subject.inOwnTransaction { subject.codes.keep(ANN, spent) }

            subject.inOwnTransaction { subject.codes.of(ANN) }?.told() shouldBe spent.told()
        }

        "keeping a set replaces every code the account had" {
            val subject = fresh()
            subject.inOwnTransaction { subject.enrollments.keep(ANN, activeAt(1)) }
            subject.inOwnTransaction { subject.codes.keep(ANN, RecoveryCodes.issue((1..10).map(::digest))) }
            val second = RecoveryCodes.issue((21..23).map(::digest))
            subject.inOwnTransaction { subject.codes.keep(ANN, second) }

            subject.inOwnTransaction { subject.codes.of(ANN) }?.told() shouldBe second.told()
        }

        "recovery codes are only kept for an account that has an enrolment" {
            val subject = fresh()

            shouldThrow<IllegalStateException> {
                subject.inOwnTransaction { subject.codes.keep(ANN, RecoveryCodes.issue(listOf(digest(1)))) }
            }
        }

        "forgetting the enrolment forgets the recovery codes with it, and forgetting nothing changes nothing" {
            val subject = fresh()
            subject.inOwnTransaction { subject.enrollments.keep(ANN, activeAt(1)) }
            subject.inOwnTransaction { subject.codes.keep(ANN, RecoveryCodes.issue(listOf(digest(1)))) }
            subject.inOwnTransaction { subject.enrollments.keep(BOB, activeAt(1)) }

            subject.inOwnTransaction { subject.enrollments.forget(ANN) }
            subject.inOwnTransaction { subject.enrollments.forget(ANN) }

            subject.inOwnTransaction { subject.enrollments.find(ANN) } shouldBe null
            subject.inOwnTransaction { subject.codes.of(ANN) } shouldBe null
            subject.inOwnTransaction { subject.enrollments.find(BOB) } shouldNotBe null
        }
    }
}
