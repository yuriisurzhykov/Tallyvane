package tallyvane.models.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

private val ONE = Uuid.parse("00000000-0000-7000-8000-0000000000e1")

class RemoveProviderSpec :
    StringSpec({
        "removes a provider once" {
            val world = World()
            world.book.save(world.form(ONE))
            val remove = RemoveProviderUseCase.RemoveProvider(world.administration)

            remove.remove(ADMIN, ONE)

            remove.remove(ADMIN, ONE) shouldBe ModelsOutcome.Failed.Missing()
        }
    })
