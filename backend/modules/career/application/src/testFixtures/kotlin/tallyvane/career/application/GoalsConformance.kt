package tallyvane.career.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.career.application.port.Goals
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")
private val BOB = Uuid.parse("00000000-0000-7000-8000-0000000000a2")

/**
 * The behaviour every [Goals] must show (ADR-046).
 */
abstract class GoalsConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val goals: Goals
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwn(call: () -> T): T = transactions.inTransaction { Verdict.Commit(call()) }

    init {
        "has no goal for a person who chose none" {
            val subject = fresh()

            subject.inOwn { subject.goals.of(ADA) } shouldBe null
        }

        "keeps the latest goal of each person apart" {
            val subject = fresh()

            subject.inOwn { subject.goals.set(ADA, "staff-android") }
            subject.inOwn { subject.goals.set(ADA, "engineering-manager") }
            subject.inOwn { subject.goals.set(BOB, "staff-android") }

            subject.inOwn { subject.goals.of(ADA) } shouldBe "engineering-manager"
            subject.inOwn { subject.goals.of(BOB) } shouldBe "staff-android"
        }
    }
}
