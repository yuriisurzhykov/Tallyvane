package tallyvane.models.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

private val ONE = Uuid.parse("00000000-0000-7000-8000-0000000000e1")

class ChooseProviderSpec :
    StringSpec({
        "makes the chosen provider the active one, and answers missing for one that is not there" {
            val world = World()
            world.book.save(world.form(ONE))
            val choose = ChooseProviderUseCase.ChooseProvider(world.administration)

            choose.choose(ADMIN, ONE)
            choose.choose(ADMIN, Uuid.NIL) shouldBe ModelsOutcome.Failed.Missing()

            world.book.list().single().active shouldBe true
        }
    })
