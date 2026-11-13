package tallyvane.stories.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.catalog.contract.SkillCatalogFake
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.platform.kernel.UntrustedText
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
private val FACT = Uuid.parse("00000000-0000-7000-8000-000000000001")
private const val WORDS = "Я сам предложил очередь платежей."
private val NOWHERE = Place(null, null, null, null)

class CorrectFactSpec :
    StringSpec({
        fun correcting(reading: Reading): Pair<StoriesFake, CorrectFactUseCase> {
            val stories = StoriesFake()
            val claim =
                Claim(
                    FactKind.Decision,
                    "proposed a queue",
                    listOf("предложил очередь"),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    true,
                )
            val hearing = Hearing(checkNotNull(UntrustedText.of(WORDS)), STORY, emptySet())
            stories.save(Story.begun(STORY, ADA).hear(hearing, listOf(claim), NOWHERE, null, IdGeneratorFake()))
            val transactions = TransactionRunnerFake()
            val ids = IdGeneratorFake(100)
            val listening =
                Listening(UtterancesFake(), transactions, ClockFake(Instant.parse("2026-11-13T10:00:00Z")), ids)
            return stories to
                CorrectFactUseCase.CorrectFact(
                    stories,
                    ReaderFake(reading),
                    listening,
                    SkillCatalogFake(),
                    transactions,
                    ids,
                )
        }

        "replaces the fact with one that quotes the correction" {
            val fixed =
                Claim(
                    FactKind.Decision,
                    "the team chose a queue",
                    listOf("выбрала команда"),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    false,
                )
            val (stories, correct) = correcting(Reading.Heard(listOf(fixed), NOWHERE, null))

            correct.correct(ADA, STORY, FACT, "Очередь выбрала команда, не я") shouldBe
                StoryOutcome.Told(checkNotNull(stories.find(STORY)))
            stories.find(STORY)?.live(FACT) shouldBe null
        }

        "keeps the fact when the correction quotes nothing of the new words" {
            val invented =
                Claim(FactKind.Decision, "led a team", listOf("руководил"), emptyList(), emptyList(), emptyList(), true)
            val (stories, correct) = correcting(Reading.Heard(listOf(invented), NOWHERE, null))

            correct.correct(ADA, STORY, FACT, "Очередь выбрала команда") shouldBe StoryOutcome.Failed.Unheard()
            (stories.find(STORY)?.live(FACT) != null) shouldBe true
        }

        "does not let another person correct the fact" {
            val (_, correct) = correcting(Reading.OverBudget())

            correct.correct(BOB, STORY, FACT, "Всё не так") shouldBe StoryOutcome.Failed.Missing()
        }
    })
