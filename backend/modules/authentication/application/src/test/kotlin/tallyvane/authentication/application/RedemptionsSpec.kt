package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.contract.Proof
import tallyvane.authentication.contract.Redemption
import tallyvane.identity.contract.AccountId
import tallyvane.platform.kernel.Digest
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Surface
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private fun vouched(subject: String) = GoogleAnswer.Vouched(subject, GoogleProfile("Ann Example", "ann@example.com"))

private fun told(redemption: Redemption): String = redemption.reportTo(
    object : Redemption.Report<String> {
        override fun redeemed(account: AccountId, proofs: Set<Proof>, authenticatedAt: Instant): String =
            "redeemed ${account.value} $proofs $authenticatedAt"

        override fun nothingToRedeem(): String = "nothing"
    },
)

/**
 * Attempts where another request deleted the attempt between this one reading and forgetting it.
 */
private class LosingTheRace(private val real: Attempts) : Attempts by real {
    override fun forget(key: Digest): Boolean {
        real.forget(key)
        return false
    }
}

class RedemptionsSpec :
    StringSpec(
        {
            "hands over the completed sign-in of a person with an account, with how they proved it and when" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn()
                harness.returnWith(pressed, vouched("sub-1"))

                told(harness.redemptions.redeem(pressed.attempt)) shouldBe
                    "redeemed 00000000-0000-7000-8000-000000000001 [Google] 2026-10-02T09:00:00Z"
            }

            "hands the completed sign-in of an administrator over as an administrator's, never as a person's" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val totp = harness.enableTotp("sub-1")
                val pressed = harness.pressSignIn(Surface.Admin)
                harness.returnWith(pressed, vouched("sub-1"))
                harness.verify.verify(pressed.attempt, Submission.TotpCode(totp.app.codeAt(harness.clock.now())))

                told(harness.redemptions.redeem(pressed.attempt)) shouldBe "nothing"
                told(harness.redemptions.redeemAdminLogin(pressed.attempt)).startsWith("redeemed") shouldBe true
            }

            "a person's sign-in cannot be taken as an administrator's" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn()
                harness.returnWith(pressed, vouched("sub-1"))

                told(harness.redemptions.redeemAdminLogin(pressed.attempt)) shouldBe "nothing"
                told(harness.redemptions.redeem(pressed.attempt)).startsWith("redeemed") shouldBe true
            }

            "an administrator who has not turned TOTP on has nothing to hand over, and the sign-in stays kept" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn(Surface.Admin)
                harness.returnWith(pressed, vouched("sub-1"))

                told(harness.redemptions.redeemAdminLogin(pressed.attempt)) shouldBe "nothing"
                harness.standing(pressed.attempt) shouldBe "restricted [Totp, RecoveryCode]"
            }

            "hands a sign-in over once" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn()
                harness.returnWith(pressed, vouched("sub-1"))
                harness.redemptions.redeem(pressed.attempt)

                told(harness.redemptions.redeem(pressed.attempt)) shouldBe "nothing"
            }

            "gives nothing to a request that read the sign-in but lost the race to take it" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn()
                harness.returnWith(pressed, vouched("sub-1"))
                val losing = harness.redemptionsOver(LosingTheRace(harness.store))

                told(losing.redeem(pressed.attempt)) shouldBe "nothing"
            }

            "has nothing to hand over for a sign-in that Google has not answered yet" {
                val harness = Harness()
                val pressed = harness.pressSignIn()

                told(harness.redemptions.redeem(pressed.attempt)) shouldBe "nothing"
            }

            "has nothing to hand over for a person whose welcome form is not finished, and leaves it for the form" {
                val harness = Harness()
                val registering = Secret(
                    harness.line(
                        harness.returnWith(harness.pressSignIn(), vouched("sub-1")),
                    ).substringAfter("registering "),
                )

                told(harness.redemptions.redeem(registering)) shouldBe "nothing"

                harness.register.register(registering, "Ann", agreed = true) shouldBe RegisterOutcome.Registered()
                told(harness.redemptions.redeem(registering)).substringBefore(" ") shouldBe "redeemed"
            }

            "has nothing to hand over for a secret nobody was given" {
                told(Harness().redemptions.redeem(Secret("guess"))) shouldBe "nothing"
            }

            "has nothing to hand over once the sign-in has expired" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn()
                harness.returnWith(pressed, vouched("sub-1"))
                harness.clock.passes(16.minutes)

                told(harness.redemptions.redeem(pressed.attempt)) shouldBe "nothing"
            }

            "hands over a completed confirmation as a confirmation, with who proved it and when" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressStepUp()
                harness.returnWith(pressed, vouched("sub-1"))

                told(harness.redemptions.redeemStepUp(pressed.attempt)) shouldBe
                    "redeemed 00000000-0000-7000-8000-000000000001 [Google] 2026-10-02T09:00:00Z"
            }

            "hands a confirmation over once" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressStepUp()
                harness.returnWith(pressed, vouched("sub-1"))
                harness.redemptions.redeemStepUp(pressed.attempt)

                told(harness.redemptions.redeemStepUp(pressed.attempt)) shouldBe "nothing"
            }

            "a confirmation cannot be taken as a sign-in, so it never grants a session" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressStepUp()
                harness.returnWith(pressed, vouched("sub-1"))

                told(harness.redemptions.redeem(pressed.attempt)) shouldBe "nothing"
                told(harness.redemptions.redeemStepUp(pressed.attempt)).startsWith("redeemed") shouldBe true
            }

            "a sign-in cannot be taken as a confirmation, so it cannot confirm another session" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                val pressed = harness.pressSignIn()
                harness.returnWith(pressed, vouched("sub-1"))

                told(harness.redemptions.redeemStepUp(pressed.attempt)) shouldBe "nothing"
                told(harness.redemptions.redeem(pressed.attempt)).startsWith("redeemed") shouldBe true
            }

            "a confirmation by a Google account nobody here knows is not handed over" {
                val harness = Harness()
                val pressed = harness.pressStepUp()
                harness.returnWith(pressed, vouched("stranger"))

                told(harness.redemptions.redeemStepUp(pressed.attempt)) shouldBe "nothing"
            }
        },
    )
