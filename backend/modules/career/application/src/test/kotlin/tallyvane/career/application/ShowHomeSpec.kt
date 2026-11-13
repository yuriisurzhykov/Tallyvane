package tallyvane.career.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.catalog.contract.SkillCatalogFake
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.stories.contract.ExperienceFake
import tallyvane.stories.contract.ToldStory
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")

class ShowHomeSpec :
    StringSpec({
        val told = Told()

        suspend fun home(stories: List<ToldStory>, goal: String?): Home {
            val goals = GoalsFake().also { goals -> goal?.let { goals.set(ADA, it) } }
            return ShowHomeUseCase.ShowHome(
                goals,
                ExperienceFake(stories),
                SkillCatalogFake(),
                TransactionRunnerFake(),
            ).show(ADA)
        }

        val finished = told.story(
            1,
            null,
            told.fact("decision", "proposed a queue", true, "architecture"),
            told.fact("outcome", "zero lost payments", true, "architecture"),
        )

        "asks for the first story before anything else" {
            home(emptyList(), "staff-android").step shouldBe "tell_first"
        }

        "asks for a goal once something is told" {
            home(listOf(finished), null).step shouldBe "set_goal"
        }

        "asks to finish a story that has a slot missing" {
            val open = told.story(2, "outcome", told.fact("action", "built it"))

            home(listOf(open, finished), "staff-android").let { it.step to it.detail } shouldBe
                ("finish_story" to "outcome")
        }

        "otherwise closes the most important gap, and confirms a skill backed twice with a result" {
            val shown = home(listOf(finished), "staff-android")

            shown.step shouldBe "close_gap"
            shown.route.first { it.skill == "architecture" }.level shouldBe Level.Confirmed
            shown.subject shouldBe shown.route.filter { it.level == Level.Gap }.maxBy { it.importance }.skill
        }
    })
