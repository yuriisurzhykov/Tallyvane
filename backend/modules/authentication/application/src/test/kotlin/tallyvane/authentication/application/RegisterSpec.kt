package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration.Companion.minutes

class RegisterSpec :
    StringSpec(
        {
            suspend fun Harness.registering(): Secret {
                val pressed = pressSignIn()
                val answer = GoogleAnswer.Vouched("sub-1", GoogleProfile("Ann Example", "ann@example.com"))
                return Secret(line(returnWith(pressed, answer)).substringAfter("registering "))
            }

            "creates the account of a person who chose a name and agreed" {
                val harness = Harness()
                val cookie = harness.registering()

                harness.register.register(cookie, "Ann", agreed = true) shouldBe RegisterOutcome.Registered()

                harness.accounts.knowing("sub-1") shouldBe true
            }

            "creates nothing without agreement" {
                val harness = Harness()
                val cookie = harness.registering()

                harness.register.register(cookie, "Ann", agreed = false) shouldBe
                    RegisterOutcome.Failed.ConsentMissing()

                harness.accounts.knowing("sub-1") shouldBe false
            }

            "creates nothing for a name identity refuses, and the person may try another" {
                val harness = Harness()
                val cookie = harness.registering()

                harness.register.register(cookie, " ", agreed = true) shouldBe RegisterOutcome.Failed.NameRefused()
                harness.register.register(cookie, "Ann", agreed = true) shouldBe RegisterOutcome.Registered()
            }

            "submitting twice finds the same account" {
                val harness = Harness()
                val cookie = harness.registering()
                harness.register.register(cookie, "Ann", agreed = true)

                harness.register.register(cookie, "Ann", agreed = true) shouldBe RegisterOutcome.Registered()
            }

            "has nothing to finish without a cookie, with a secret nobody was given, or once expired" {
                val harness = Harness()
                val cookie = harness.registering()

                harness.register.register(null, "Ann", agreed = true) shouldBe RegisterOutcome.Failed.NoRegistration()
                harness.register.register(Secret("guess"), "Ann", agreed = true) shouldBe
                    RegisterOutcome.Failed.NoRegistration()
                harness.clock.passes(6.minutes)
                harness.register.register(cookie, "Ann", agreed = true) shouldBe RegisterOutcome.Failed.NoRegistration()
            }

            "does not register a sign-in that is not a registration" {
                val harness = Harness()
                val pressed = harness.pressSignIn()

                harness.register.register(pressed.attempt, "Ann", agreed = true) shouldBe
                    RegisterOutcome.Failed.NoRegistration()
            }
        },
    )
