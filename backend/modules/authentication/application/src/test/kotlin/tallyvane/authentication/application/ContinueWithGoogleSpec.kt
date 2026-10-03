package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration.Companion.minutes

private fun vouched(subject: String) = GoogleAnswer.Vouched(subject, GoogleProfile("Ann Example", "ann@example.com"))

class ContinueWithGoogleSpec :
    StringSpec(
        {
            "a person with an account goes on with their sign-in" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn()

                harness.line(harness.returnWith(pressed, vouched("sub-1"))) shouldBe "verified"
            }

            "a person without an account gets a registration under a new secret, and the old one stops working" {
                val harness = Harness()
                val pressed = harness.pressSignIn()

                val outcome = harness.line(harness.returnWith(pressed, vouched("sub-1")))

                outcome shouldBe "registering secret-5"
                harness.line(harness.returnWith(pressed, vouched("sub-1"))) shouldBe "turned back: Restart"
            }

            "the registration shows what Google said, and no account exists yet" {
                val harness = Harness()
                harness.returnWith(harness.pressSignIn(), vouched("sub-1"))
                val told = mutableListOf<String>()

                (harness.show.show(Secret("secret-5")) as ShowRegistrationOutcome.Welcome)
                    .writeTo { name, email -> told += "$name <$email>" }

                told shouldBe listOf("Ann Example <ann@example.com>")
                harness.accounts.knowing("sub-1") shouldBe false
            }

            "a reply that arrives twice is answered once" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn()
                harness.returnWith(pressed, vouched("sub-1"))

                harness.line(harness.returnWith(pressed, vouched("sub-1"))) shouldBe "turned back: Restart"
            }

            "no cookie means there is nothing to continue" {
                val harness = Harness()
                val pressed = harness.pressSignIn()

                harness.line(harness.returnWith(pressed, vouched("sub-1"), cookie = null)) shouldBe
                    "turned back: Restart"
            }

            "a state that is not the one sent stops the sign-in and forgets it" {
                val harness = Harness()
                val pressed = harness.pressSignIn()
                val code = harness.google.arrange(pressed.state, vouched("sub-1"))

                val forged = harness.continueWith.continueWith(
                    pressed.attempt,
                    GoogleReply.Granted(code, "other-state"),
                )

                harness.line(forged) shouldBe "turned back: Restart"
                harness.line(harness.returnWith(pressed, vouched("sub-1"))) shouldBe "turned back: Restart"
            }

            "a person who said no at Google is turned back" {
                val harness = Harness()
                val pressed = harness.pressSignIn()

                harness.line(harness.continueWith.continueWith(pressed.attempt, GoogleReply.Declined())) shouldBe
                    "turned back: Cancelled"
            }

            "a sign-in that outlived its lifetime while the person was at Google is turned back" {
                val harness = Harness()
                val pressed = harness.pressSignIn()
                harness.clock.passes(6.minutes)

                harness.line(harness.returnWith(pressed, vouched("sub-1"))) shouldBe "turned back: Expired"
            }

            "an address Google has not verified is turned back" {
                val harness = Harness()
                val pressed = harness.pressSignIn()

                harness.line(harness.returnWith(pressed, GoogleAnswer.EmailUnverified())) shouldBe
                    "turned back: EmailUnverified"
            }

            "a code Google does not accept is turned back" {
                val harness = Harness()
                val pressed = harness.pressSignIn()

                harness.line(harness.returnWith(pressed, GoogleAnswer.Refused())) shouldBe "turned back: Refused"
            }

            "Google being down is turned back as such, and the sign-in is not kept for a retry" {
                val harness = Harness()
                val pressed = harness.pressSignIn()
                harness.google.goingDown()

                harness.line(harness.returnWith(pressed, vouched("sub-1"))) shouldBe "turned back: Unavailable"
                harness.line(harness.returnWith(pressed, vouched("sub-1"))) shouldBe "turned back: Restart"
            }

            "a person confirming a dangerous act, who has an account, goes on to be taken by the session" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressStepUp()

                harness.line(harness.returnWith(pressed, vouched("sub-1"))) shouldBe "stepped up"
            }

            "a confirmation with a Google account nobody here knows never becomes a registration" {
                val harness = Harness()
                val pressed = harness.pressStepUp()

                harness.line(harness.returnWith(pressed, vouched("stranger"))) shouldBe "stepped up"
                harness.accounts.knowing("stranger") shouldBe false
            }
        },
    )
