package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BeginSignInSpec :
    StringSpec(
        {
            "hands the browser a secret and sends Google a state that is not that secret" {
                val pressed = Harness().pressSignIn()

                pressed.attempt.revealed() shouldBe "secret-1"
                pressed.state shouldBe "secret-2"
            }

            "keeps the attempt under the digest of the secret, so the cookie finds it and the state does not" {
                val harness = Harness()
                val pressed = harness.pressSignIn()
                val other = harness.pressSignIn()

                // Google's state is what the redirect carries; the cookie is what finds the attempt.
                harness.line(harness.returnWith(pressed, GoogleAnswer.Refused())) shouldBe "turned back: Refused"
                harness.line(
                    harness.returnWith(
                        other,
                        GoogleAnswer.Refused(),
                        cookie = tallyvane.platform.kernel.Secret(other.state),
                    ),
                ) shouldBe "turned back: Restart"
            }

            "two sign-ins get secrets of their own" {
                val harness = Harness()

                harness.pressSignIn().attempt shouldNotBe harness.pressSignIn().attempt
            }
        },
    )
