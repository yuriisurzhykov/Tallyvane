package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import tallyvane.platform.kernel.Surface

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

            "on the administrators' door the attempt is an administrator's, which asks everyone for TOTP" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn(Surface.Admin)
                harness.returnWith(
                    pressed,
                    GoogleAnswer.Vouched("sub-1", GoogleProfile("Ann Example", "ann@example.com")),
                )

                harness.standing(pressed.attempt) shouldBe "restricted [Totp, RecoveryCode]"
            }

            "on the console's door the same person is signed in with Google alone until they turn TOTP on" {
                val harness = Harness()
                harness.accounts.knows("sub-1")

                harness.standing(harness.signedInWithGoogle("sub-1")) shouldBe "complete [Google]"
            }

            "two sign-ins get secrets of their own" {
                val harness = Harness()

                harness.pressSignIn().attempt shouldNotBe harness.pressSignIn().attempt
            }
        },
    )
