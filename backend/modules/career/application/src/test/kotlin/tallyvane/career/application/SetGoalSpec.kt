package tallyvane.career.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.catalog.contract.SkillCatalogFake
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.uuid.Uuid

private val ADA = Uuid.parse("00000000-0000-7000-8000-0000000000a1")

class SetGoalSpec :
    StringSpec({
        "keeps a role the catalog lists and refuses one it does not" {
            val goals = GoalsFake()
            val set = SetGoalUseCase.SetGoal(goals, SkillCatalogFake(), TransactionRunnerFake())

            set.set(ADA, "staff-android") shouldBe GoalOutcome.Set("staff-android")
            set.set(ADA, "astronaut") shouldBe GoalOutcome.Failed.UnknownRole()
            goals.of(ADA) shouldBe "staff-android"
        }
    })
