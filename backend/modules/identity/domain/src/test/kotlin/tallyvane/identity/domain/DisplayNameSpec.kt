package tallyvane.identity.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun DisplayName.told(): String = mutableListOf<String>().also { names -> writeTo { names += it } }.single()

class DisplayNameSpec :
    StringSpec(
        {
            "a name is kept trimmed" {
                DisplayName.of("  Ada Lovelace \t")?.told() shouldBe "Ada Lovelace"
            }

            "a name of one character and one of the longest allowed are both accepted" {
                DisplayName.of("A")?.told() shouldBe "A"
                DisplayName.of("x".repeat(DisplayName.MAX_LENGTH))?.told() shouldBe "x".repeat(DisplayName.MAX_LENGTH)
            }

            "an empty name, a blank one and one a character too long are refused" {
                DisplayName.of("").shouldBeNull()
                DisplayName.of("   ").shouldBeNull()
                DisplayName.of("x".repeat(DisplayName.MAX_LENGTH + 1)).shouldBeNull()
            }

            "a name with a line break or an escape inside it is refused" {
                DisplayName.of("Ada\nLovelace").shouldBeNull()
                DisplayName.of("Ada\u001B[31mLovelace").shouldBeNull()
            }

            "letters outside ASCII are names like any other" {
                DisplayName.of("Юрий Суржиков")?.told() shouldBe "Юрий Суржиков"
            }

            "two names are the same when their text is, after trimming" {
                DisplayName.of(" Ada ") shouldBe DisplayName.of("Ada")
            }
        },
    )
