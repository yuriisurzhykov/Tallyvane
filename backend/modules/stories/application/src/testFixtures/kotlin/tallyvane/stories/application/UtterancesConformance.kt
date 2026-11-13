package tallyvane.stories.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UntrustedText
import tallyvane.platform.kernel.Verdict
import tallyvane.stories.application.port.Utterances
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")
private val BOB = Uuid.parse("00000000-0000-7000-8000-0000000000a2")
private val STORY = Uuid.parse("00000000-0000-7000-8000-0000000000b1")
private val AT = Instant.parse("2026-11-13T10:00:00Z")

/**
 * The behaviour every [Utterances] must show (ADR-046).
 */
abstract class UtterancesConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val utterances: Utterances
        val transactions: TransactionRunner
    }

    init {
        "counts each person's words apart" {
            val subject = fresh()
            val words = checkNotNull(UntrustedText.of("Я сам предложил очередь."))

            subject.transactions.inTransaction {
                subject.utterances.keep(Uuid.fromLongs(1, 1), ADA, STORY, words, AT)
                subject.utterances.keep(Uuid.fromLongs(1, 2), ADA, STORY, words, AT)
                Verdict.Commit(subject.utterances.keep(Uuid.fromLongs(1, 3), BOB, STORY, words, AT))
            }

            subject.transactions.inTransaction { Verdict.Commit(subject.utterances.countOf(ADA)) } shouldBe 2
        }
    }
}
