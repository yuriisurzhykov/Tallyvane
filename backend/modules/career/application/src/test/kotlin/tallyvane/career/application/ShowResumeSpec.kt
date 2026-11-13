package tallyvane.career.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.catalog.contract.SkillCatalogFake
import tallyvane.stories.contract.ExperienceFake
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")

class ShowResumeSpec :
    StringSpec({
        val told = Told()

        "puts results first and never claims what the team did" {
            val story = told.story(
                1,
                null,
                told.fact("action", "built the consumer"),
                told.fact("decision", "the team chose Kafka", false),
                told.fact("outcome", "zero lost payments"),
                told.fact("context", "legacy call lost payments"),
            )

            val resume = ShowResumeUseCase.ShowResume(ExperienceFake(listOf(story)), SkillCatalogFake()).show(ADA)

            resume.roles.single().bullets shouldBe listOf("zero lost payments", "built the consumer")
        }

        "is empty when nothing the person did was told" {
            ShowResumeUseCase.ShowResume(ExperienceFake(), SkillCatalogFake()).show(ADA).roles shouldBe emptyList()
        }
    })
