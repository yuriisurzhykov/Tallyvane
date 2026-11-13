package tallyvane.stories.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UntrustedText
import tallyvane.platform.kernel.Verdict
import tallyvane.stories.application.port.Stories
import tallyvane.stories.application.port.Utterances
import tallyvane.stories.domain.Claim
import tallyvane.stories.domain.FactKind
import tallyvane.stories.domain.Hearing
import tallyvane.stories.domain.Place
import tallyvane.stories.domain.Story
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")
private val BOB = Uuid.parse("00000000-0000-7000-8000-0000000000a2")
private val STORY = Uuid.parse("00000000-0000-7000-8000-0000000000b1")
private val SAID = Uuid.parse("00000000-0000-7000-8000-0000000000c1")
private const val WORDS = "В Acme я сам предложил очередь платежей, и к концу квартала ни одного потерянного."

/**
 * The behaviour every [Stories] and [Utterances] must show, so the fakes and Postgres cannot disagree (ADR-046).
 */
abstract class StoriesConformance : StringSpec() {
    protected abstract suspend fun fresh(): Subject

    interface Subject {
        val stories: Stories
        val utterances: Utterances
        val transactions: TransactionRunner
    }

    private suspend fun <T> Subject.inOwn(call: () -> T): T = transactions.inTransaction { Verdict.Commit(call()) }

    private fun told(): Story {
        val claims = listOf(
            Claim(
                FactKind.Decision,
                "proposed a payment queue",
                listOf("я сам предложил очередь платежей"),
                emptyList(),
                listOf("Kafka"),
                listOf("architecture"),
                true,
            ),
            Claim(
                FactKind.Outcome,
                "zero lost payments",
                listOf("ни одного потерянного"),
                listOf(0.0),
                emptyList(),
                emptyList(),
                true,
            ),
        )
        val hearing = Hearing(checkNotNull(UntrustedText.of(WORDS)), SAID, setOf("architecture"))
        return Story.begun(
            STORY,
            ADA,
        ).hear(hearing, claims, Place("Acme", "Engineer", "2023-01", null), "Payments", IdGeneratorFake())
    }

    init {
        "keeps a story with its place, facts and quotes, and gives back an equal one" {
            val subject = fresh()
            subject.inOwn {
                subject.utterances.keep(
                    SAID,
                    ADA,
                    STORY,
                    checkNotNull(UntrustedText.of(WORDS)),
                    Instant.parse("2026-11-13T10:00:00Z"),
                )
            }

            subject.inOwn { subject.stories.save(told()) }

            subject.inOwn { subject.stories.find(STORY) } shouldBe told()
            subject.inOwn { subject.stories.of(ADA) } shouldBe listOf(told())
            subject.inOwn { subject.stories.of(BOB) } shouldBe emptyList()
            subject.inOwn { subject.utterances.countOf(ADA) } shouldBe 1
        }

        "replaces a story saved again under the same id" {
            val subject = fresh()
            val begun = Story.begun(STORY, ADA)
            subject.inOwn {
                subject.utterances.keep(
                    SAID,
                    ADA,
                    STORY,
                    checkNotNull(UntrustedText.of(WORDS)),
                    Instant.parse("2026-11-13T10:00:00Z"),
                )
            }

            subject.inOwn { subject.stories.save(begun) }
            subject.inOwn { subject.stories.save(told()) }

            subject.inOwn { subject.stories.of(ADA) } shouldBe listOf(told())
        }

        "finds nothing it was not given" {
            fresh().let { it.inOwn { it.stories.find(STORY) } } shouldBe null
        }
    }
}
