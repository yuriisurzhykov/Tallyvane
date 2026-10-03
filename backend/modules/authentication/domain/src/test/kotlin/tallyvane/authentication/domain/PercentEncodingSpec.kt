package tallyvane.authentication.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PercentEncodingSpec :
    StringSpec(
        {
            "leaves unreserved characters alone" {
                PercentEncoding().of("Tally-vane_1.0~") shouldBe "Tally-vane_1.0~"
            }

            "writes a space as %20, never as a plus" {
                PercentEncoding().of("My App") shouldBe "My%20App"
            }

            "writes the UTF-8 bytes of what is outside ASCII" {
                PercentEncoding().of("Tällyvane") shouldBe "T%C3%A4llyvane"
            }

            "writes what would end a URI component" {
                PercentEncoding().of("a:b/c?d&e=f#g") shouldBe "a%3Ab%2Fc%3Fd%26e%3Df%23g"
            }
        },
    )
