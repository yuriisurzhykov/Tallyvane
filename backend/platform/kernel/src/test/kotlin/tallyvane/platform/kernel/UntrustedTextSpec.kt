package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UntrustedTextSpec :
    StringSpec(
        {
            "keeps what a person can see, in any language, with its line breaks" {
                UntrustedText.of("  Я сам предложил очередь.\r\nKotlin, 300/s  ")?.cleaned() shouldBe
                    "Я сам предложил очередь.\nKotlin, 300/s"
            }

            "drops characters nobody can see: zero-width, direction marks, tags, control codes" {
                UntrustedText.of("pay​ments‮\u0007 󠁉ok")?.cleaned() shouldBe "payments ok"
            }

            "is nothing when only blanks or invisible characters were sent" {
                UntrustedText.of(" \n\t​ ") shouldBe null
            }

            "is nothing past the limit, and something at it" {
                UntrustedText.of("a".repeat(UntrustedText.LIMIT))?.cleaned()?.length shouldBe UntrustedText.LIMIT
                UntrustedText.of("a".repeat(UntrustedText.LIMIT + 1)) shouldBe null
            }
        },
    )
