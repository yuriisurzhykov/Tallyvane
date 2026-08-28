package tallyvane.platform.kernel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class EnvironmentFakeSpec :
    StringSpec(
        {
            "reads back what it was given" {
                EnvironmentFake(mapOf("A" to "1")).read("A") shouldBe "1"
            }

            "reports a name it was not given as absent, rather than as empty" {
                EnvironmentFake(mapOf("A" to "1")).read("B").shouldBeNull()
            }

            "defines nothing by default, which is the state most configuration cases describe" {
                EnvironmentFake().read("A").shouldBeNull()
            }
        },
    )
