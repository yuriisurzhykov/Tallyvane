package tallyvane.stories.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.stories.domain.Story
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")
private val BOB = Uuid.parse("00000000-0000-7000-8000-0000000000a2")

class ShowStoriesSpec :
    StringSpec({
        "shows only the person's own stories" {
            val stories = StoriesFake()
            stories.save(Story.begun(Uuid.parse("00000000-0000-7000-8000-0000000000b1"), ADA))
            stories.save(Story.begun(Uuid.parse("00000000-0000-7000-8000-0000000000b2"), BOB))

            ShowStoriesUseCase.ShowStories(stories, TransactionRunnerFake()).show(ADA).toString() shouldBe "Shelf(1)"
        }
    })
