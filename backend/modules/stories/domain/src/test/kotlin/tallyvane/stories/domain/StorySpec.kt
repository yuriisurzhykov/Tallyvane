package tallyvane.stories.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.UntrustedText
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")
private val STORY = Uuid.parse("00000000-0000-7000-8000-0000000000b1")
private val FIRST = Uuid.parse("00000000-0000-7000-8000-0000000000c1")
private val SECOND = Uuid.parse("00000000-0000-7000-8000-0000000000c2")
private val SKILLS = setOf("architecture", "kotlin")
private const val TOLD =
    "Старый вызов терял платежи, я сам предложил очередь на Kotlin, к концу квартала ни одного потерянного."
private const val SCALE = "Два миллиона клиентов, в пятницу до 300 платежей в секунду."
private val NOWHERE = Place(null, null, null, null)

private fun hearing(text: String, utterance: Uuid = FIRST): Hearing =
    Hearing(checkNotNull(UntrustedText.of(text)), utterance, SKILLS)

private fun claim(
    kind: FactKind,
    text: String,
    fragment: String,
    figures: List<Double> = emptyList(),
    skills: List<String> = emptyList(),
    mine: Boolean = true,
): Claim = Claim(kind, text, listOf(fragment), figures, emptyList(), skills, mine)

private val CONTEXT = claim(FactKind.Context, "legacy call lost payments", "Старый вызов терял платежи")
private val DECISION =
    claim(FactKind.Decision, "proposed a payment queue", "я сам предложил очередь", skills = listOf("architecture"))
private val OUTCOME = claim(FactKind.Outcome, "zero lost payments", "ни одного потерянного", figures = listOf(0.0))
private val SIZE = Claim(
    FactKind.Scale,
    "2M clients, 300 payments/s",
    listOf("Два миллиона клиентов", "до 300 платежей в секунду"),
    listOf(2_000_000.0, 300.0),
    emptyList(),
    emptyList(),
    true,
)

private fun told(): Story =
    Story.begun(STORY, ADA).hear(hearing(TOLD), listOf(CONTEXT, DECISION, OUTCOME), NOWHERE, null, IdGeneratorFake())

/**
 * What a story told, to assert on.
 */
private class Seen : Story.Record {
    val facts = mutableListOf<String>()
    val replaced = mutableListOf<String>()
    val skills = mutableListOf<String>()
    val quotes = mutableListOf<String>()
    var state: StoryState? = null
    var missing: Slot? = null
    var company: String? = null
    var started: String? = null

    override fun story(id: Uuid, account: Uuid, title: String?, finished: Boolean, state: StoryState, missing: Slot?) {
        this.state = state
        this.missing = missing
    }

    override fun place(company: String?, position: String?, started: String?, ended: String?) {
        this.company = company
        this.started = started
    }

    override fun fact(
        id: Uuid,
        kind: FactKind,
        text: String,
        figures: List<Double>,
        technologies: List<String>,
        skills: List<String>,
        mine: Boolean,
        replacedBy: Uuid?,
    ) {
        if (replacedBy == null) facts += text else replaced += text
        this.skills += skills
    }

    override fun quote(fact: Uuid, utterance: Uuid, fragment: String) {
        quotes += fragment
    }
}

private fun Story.seen(): Seen = Seen().also { writeTo(it) }

class StorySpec :
    StringSpec(
        {
            "keeps a claim whose fragment is in the words, and asks for what is missing next" {
                val seen = told().seen()

                seen.facts shouldBe
                    listOf("legacy call lost payments", "proposed a payment queue", "zero lost payments")
                seen.state shouldBe StoryState.Open
                seen.missing shouldBe Slot.Scale
            }

            "drops a claim whose fragment the person did not say" {
                val invented = claim(FactKind.Action, "led a team of 12", "руководил командой из 12 человек")

                Story.begun(STORY, ADA).hear(hearing(TOLD), listOf(invented), NOWHERE, null, IdGeneratorFake())
                    .seen().facts shouldBe emptyList()
            }

            "drops a claim whose figure is not written in its fragment" {
                val inflated =
                    claim(
                        FactKind.Scale,
                        "5M clients",
                        "Два миллиона клиентов, в пятницу до 300",
                        listOf(5_000_000.0, 301.0),
                    )

                Story.begun(STORY, ADA).hear(hearing(SCALE), listOf(inflated), NOWHERE, null, IdGeneratorFake())
                    .seen().facts shouldBe emptyList()
            }

            "accepts digits exactly and a number in words as a number" {
                val story = told().hear(hearing(SCALE, SECOND), listOf(SIZE), NOWHERE, null, IdGeneratorFake(10))

                story.seen().facts.last() shouldBe "2M clients, 300 payments/s"
                story.state() shouldBe StoryState.Strong
                story.missing() shouldBe null
            }

            "reads grouped thousands as one number" {
                Quote.restore(FIRST, "около 2 000 000 клиентов и 1,5 секунды").backs(2_000_000.0) shouldBe true
                Quote.restore(FIRST, "около 2 000 000 клиентов и 1,5 секунды").backs(1.5) shouldBe true
                Quote.restore(FIRST, "около 2 000 000 клиентов").backs(2_000.0) shouldBe false
                Quote.restore(FIRST, "было много клиентов").backs(2.0) shouldBe false
            }

            "keeps only the skills the catalog lists" {
                val tagged =
                    claim(
                        FactKind.Decision,
                        "proposed a queue",
                        "я сам предложил очередь",
                        skills = listOf("architecture", "wizardry"),
                    )

                Story.begun(STORY, ADA).hear(hearing(TOLD), listOf(tagged), NOWHERE, null, IdGeneratorFake())
                    .seen().skills shouldBe listOf("architecture")
            }

            "does not count the team's work as the person's role" {
                val teams = claim(FactKind.Action, "the team built a queue", "я сам предложил очередь", mine = false)
                val story = Story.begun(
                    STORY,
                    ADA,
                ).hear(hearing(TOLD), listOf(CONTEXT, teams), NOWHERE, null, IdGeneratorFake())

                story.missing() shouldBe Slot.Role
            }

            "does not keep the same fact twice" {
                told().hear(hearing(TOLD, SECOND), listOf(DECISION), NOWHERE, null, IdGeneratorFake(10))
                    .seen().facts.count { it == "proposed a payment queue" } shouldBe 1
            }

            "finishes when the person has nothing more: strong with a role and an outcome, closed without" {
                val nothing = hearing("Нет, больше нечего", SECOND)

                told().hear(nothing, emptyList(), NOWHERE, null, IdGeneratorFake(10)).state() shouldBe StoryState.Strong
                Story.begun(STORY, ADA).hear(nothing, emptyList(), NOWHERE, null, IdGeneratorFake()).state() shouldBe
                    StoryState.Closed
            }

            "does not take a long answer that starts with a refusal as the end" {
                val answer =
                    hearing("Нет проблем с кэшем не было, я ещё добавил потребителя Kafka для платежей", SECOND)

                told().hear(answer, emptyList(), NOWHERE, null, IdGeneratorFake(10)).missing() shouldBe Slot.Scale
            }

            "fills an unknown place and never overwrites a known one, and drops a period that is not one" {
                val first = told().hear(
                    hearing(TOLD, SECOND),
                    emptyList(),
                    Place("Восток", "Android", "2019", "вчера"),
                    null,
                    IdGeneratorFake(10),
                )
                val second = first.hear(
                    hearing(TOLD, SECOND),
                    emptyList(),
                    Place("Запад", null, "2021", "2024-13"),
                    null,
                    IdGeneratorFake(20),
                )

                second.seen().company shouldBe "Восток"
                second.seen().started shouldBe "2019"
            }

            "a correction replaces the fact, keeps the old one as replaced, and may lean on the old quote" {
                val story = told().hear(hearing(SCALE, SECOND), listOf(SIZE), NOWHERE, null, IdGeneratorFake(10))
                val old = Uuid.parse("00000000-0000-7000-8000-000000000011")
                val fixed = Claim(
                    FactKind.Scale,
                    "1.5M clients, 300 payments/s",
                    listOf("а полтора", "до 300 платежей в секунду"),
                    listOf(1_500_000.0, 300.0),
                    emptyList(),
                    emptyList(),
                    true,
                )
                val third = Uuid.parse("00000000-0000-7000-8000-0000000000c3")

                val corrected = story.correct(
                    old,
                    hearing("Не два миллиона, а полтора.", third),
                    fixed,
                    IdGeneratorFake(20),
                )

                corrected shouldNotBe null
                corrected?.seen()?.replaced shouldBe listOf("2M clients, 300 payments/s")
                corrected?.seen()?.facts?.last() shouldBe "1.5M clients, 300 payments/s"
                corrected?.holds(old) shouldBe false
            }

            "a correction that quotes nothing from the new words, or names a fact the story lacks, is refused" {
                val story = told()
                val stale = claim(FactKind.Context, "something else", "Старый вызов терял платежи")
                val first = Uuid.parse("00000000-0000-7000-8000-000000000001")

                story.correct(first, hearing("Всё было иначе", SECOND), stale, IdGeneratorFake(20)) shouldBe null
                story.correct(SECOND, hearing(TOLD, SECOND), CONTEXT, IdGeneratorFake(20)) shouldBe null
            }
        },
    )
