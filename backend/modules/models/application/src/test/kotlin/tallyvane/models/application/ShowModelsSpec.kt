package tallyvane.models.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ShowModelsSpec :
    StringSpec({
        "shows the providers to an administrator only" {
            val world = World()
            val show = ShowModelsUseCase.ShowModels(world.administration)

            (show.show(ADMIN) as ModelsOutcome.Shown).providers shouldBe emptyList()
            show.show(PERSON) shouldBe ModelsOutcome.Failed.NotAnAdministrator()
        }
    })
