package tallyvane.stories.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.catalog.contract.SkillCatalogFake
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.stories.application.port.Reader
import tallyvane.stories.domain.Claim
import tallyvane.stories.domain.FactKind
import tallyvane.stories.domain.Place
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")
private val BOB = Uuid.parse("00000000-0000-7000-8000-0000000000a2")
private const val WORDS = "Я сам предложил очередь платежей, и ни одного потерянного."
private val HEARD = Reading.Heard(
    listOf(
        Claim(
            FactKind.Decision,
            "proposed a queue",
            listOf("я сам предложил очередь"),
            emptyList(),
            emptyList(),
            listOf("architecture"),
            true,
        ),
    ),
    Place("Acme", null, null, null),
    "Payments",
)

class TellSpec :
    StringSpec({
        class World(reader: Reader) {
            val stories = StoriesFake()
            val utterances = UtterancesFake()
            private val transactions = TransactionRunnerFake()
            private val ids = IdGeneratorFake()
            val tell = TellUseCase.Tell(
                stories,
                reader,
                Listening(utterances, transactions, ClockFake(Instant.parse("2026-11-13T10:00:00Z")), ids),
                SkillCatalogFake(),
                transactions,
                ids,
            )
        }

        "keeps the words and the facts that quote them in a new story" {
            val world = World(ReaderFake(HEARD))

            world.tell.tell(ADA, null, WORDS).shouldBeInstanceOf<StoryOutcome.Told>()

            world.utterances.countOf(ADA) shouldBe 1
            world.stories.of(ADA).size shouldBe 1
        }

        "keeps the words even when the model is unavailable or over budget" {
            val down = World(ReaderFake(Reading.Unavailable("down")))
            val spent = World(ReaderFake(Reading.OverBudget()))

            down.tell.tell(ADA, null, WORDS) shouldBe StoryOutcome.Failed.Unavailable()
            spent.tell.tell(ADA, null, WORDS) shouldBe StoryOutcome.Failed.OverBudget()
            down.utterances.countOf(ADA) shouldBe 1
            down.stories.of(ADA) shouldBe emptyList()
        }

        "refuses blank words and keeps nothing" {
            val world = World(ReaderFake(HEARD))

            world.tell.tell(ADA, null, " ​ ") shouldBe StoryOutcome.Failed.Empty()
            world.utterances.countOf(ADA) shouldBe 0
        }

        "does not let one person add to another person's story" {
            val world = World(ReaderFake(HEARD))
            world.tell.tell(ADA, null, WORDS)
            val adas = world.stories.of(ADA).single().id()

            world.tell.tell(BOB, adas, WORDS) shouldBe StoryOutcome.Failed.Missing()
            world.utterances.countOf(BOB) shouldBe 0
        }
    })
