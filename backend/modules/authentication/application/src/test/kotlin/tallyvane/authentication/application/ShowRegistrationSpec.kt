package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration.Companion.minutes

class ShowRegistrationSpec :
    StringSpec(
        {
            suspend fun Harness.registering(): Secret {
                val pressed = pressSignIn()
                val answer = GoogleAnswer.Vouched("sub-1", GoogleProfile("Ann Example", "ann@example.com"))
                return Secret(line(returnWith(pressed, answer)).substringAfter("registering "))
            }

            "shows the name and address Google gave" {
                val harness = Harness()
                val cookie = harness.registering()
                val told = mutableListOf<String>()

                harness.show.show(cookie).shouldBeInstanceOf<ShowRegistrationOutcome.Welcome>()
                    .writeTo { name, email -> told += listOf(name, email) }

                told shouldBe listOf("Ann Example", "ann@example.com")
            }

            "shows nothing to a browser with no cookie" {
                Harness().show.show(null) shouldBe ShowRegistrationOutcome.Failed.NoRegistration()
            }

            "shows nothing to a secret nobody was given" {
                Harness().show.show(Secret("guess")) shouldBe ShowRegistrationOutcome.Failed.NoRegistration()
            }

            "shows nothing under the cookie of a sign-in that is not a registration" {
                val harness = Harness()
                val pressed = harness.pressSignIn()

                harness.show.show(pressed.attempt) shouldBe ShowRegistrationOutcome.Failed.NoRegistration()
            }

            "shows nothing once the registration expired" {
                val harness = Harness()
                val cookie = harness.registering()
                harness.clock.passes(6.minutes)

                harness.show.show(cookie) shouldBe ShowRegistrationOutcome.Failed.NoRegistration()
            }
        },
    )
