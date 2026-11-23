package tallyvane.models.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.uuid.Uuid

private val ONE = Uuid.parse("00000000-0000-7000-8000-0000000000e1")

class SaveProviderSpec :
    StringSpec({
        "keeps a valid provider for an administrator" {
            val world = World()

            SaveProviderUseCase.SaveProvider(world.administration).save(ADMIN, world.form(ONE))
                .shouldBeInstanceOf<ModelsOutcome.Shown>()
            world.book.list().size shouldBe 1
        }

        "refuses anyone who is not an administrator, and keeps nothing" {
            val world = World()

            SaveProviderUseCase.SaveProvider(world.administration).save(PERSON, world.form(ONE)) shouldBe
                ModelsOutcome.Failed.NotAnAdministrator()
            world.book.list() shouldBe emptyList()
        }

        "refuses a new provider without a key, and an OpenAI-compatible one without an address" {
            val save = SaveProviderUseCase.SaveProvider(World().administration)

            save.save(ADMIN, World().form(ONE, key = null)).shouldBeInstanceOf<ModelsOutcome.Failed.Invalid>()
            save.save(ADMIN, World().form(ONE, url = null)).shouldBeInstanceOf<ModelsOutcome.Failed.Invalid>()
        }

        "lets Anthropic go without an address" {
            val world = World()

            SaveProviderUseCase.SaveProvider(
                world.administration,
            ).save(ADMIN, world.form(ONE, kind = "anthropic", url = null))
                .shouldBeInstanceOf<ModelsOutcome.Shown>()
        }
    })
