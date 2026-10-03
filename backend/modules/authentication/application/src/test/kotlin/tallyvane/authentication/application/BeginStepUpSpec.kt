package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BeginStepUpSpec :
    StringSpec(
        {
            "hands the browser a secret and sends Google a state that is not that secret" {
                val pressed = Harness().pressStepUp()

                pressed.attempt.revealed() shouldBe "secret-1"
                pressed.state shouldBe "secret-2"
            }

            "two confirmations get secrets of their own" {
                val harness = Harness()

                harness.pressStepUp().attempt shouldNotBe harness.pressStepUp().attempt
            }

            "a confirmation that Google turned back is turned back like a sign-in" {
                val harness = Harness()
                val pressed = harness.pressStepUp()

                harness.line(harness.returnWith(pressed, GoogleAnswer.Refused())) shouldBe "turned back: Refused"
            }
        },
    )
