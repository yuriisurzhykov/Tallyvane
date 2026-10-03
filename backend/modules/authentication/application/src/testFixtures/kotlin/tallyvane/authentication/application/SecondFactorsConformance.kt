package tallyvane.authentication.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.authentication.domain.RecoveryCodes
import tallyvane.authentication.domain.SpendVerdict
import tallyvane.authentication.domain.TotpEnrollment
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ANN = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000a"))
private val BOB = AccountId(Uuid.parse("00000000-0000-7000-8000-00000000000b"))
private val ENTROPY = "12345678901234567890".toByteArray(Charsets.US_ASCII)
private val AT = Instant.parse("2026-10-03T09:00:00Z")

private fun TotpEnrollment.told(): List<String> {
    val told = mutableListOf<String>()
    writeTo { seed, standing, last -> told += listOf(seed.revealed(), standing.name, last.toString()) }
    return told
}

private fun RecoveryCodes.told(): List<String> {
    val told = mutableListOf<String>()
    writeTo { digest, spentAt ->
        val bytes = mutableListOf<Byte>()
        digest.writeTo { raw, version -> bytes += raw.toList() + version.toByte() }
        told += "${bytes.joinToString(",")} spent=$spentAt"
    }
    return told
}

private fun digest(seed: Int) = Digest(byteArrayOf(seed.toByte(), 9), 1)

private fun activeAt(step: Long): TotpEnrollment = TotpEnrollment.restore { record ->
    TotpEnrollment.begin(ENTROPY).writeTo { seed, _, _ -> record.kept(seed, TotpEnrollment.Standing.Active, step) }
}

/**
 * The behaviour every [TotpEnrollments] and [RecoveryCodeSets] must show, whatever keeps them.
 */
abstract class SecondFactorsConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val enrollments: TotpEnrollments
        val codes: RecoveryCodeSets
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwnTransaction(call: () -> T): T =
        transactions.inTransaction { Verdict.Commit(call()) }

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
