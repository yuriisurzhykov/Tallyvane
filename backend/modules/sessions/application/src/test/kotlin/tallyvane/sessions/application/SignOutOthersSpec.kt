package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret

class SignOutOthersSpec :
    StringSpec(
        {
            "signs out everywhere but here" {
                val harness = Harness()
                val here = harness.signedIn()
                val second = harness.signedIn(agent = MAC_CHROME)
                val third = harness.signedIn()
                val strangers = harness.signedIn(account = Harness.OTHER_ACCOUNT)

                harness.signOutOthers.signOutOthers(here) shouldBe DeviceOutcome.Done()

                harness.who(here) shouldBe "signed in ${Harness.ACCOUNT.value}"
                harness.who(second) shouldBe "lapsed"
                harness.who(third) shouldBe "lapsed"
                harness.who(strangers) shouldBe "signed in ${Harness.OTHER_ACCOUNT.value}"
            }

            "refuses a request with no session or an ended one" {
                val harness = Harness()
                val ended = Secret("never-issued")

                harness.signOutOthers.signOutOthers(null) shouldBe DeviceOutcome.Failed.SignInRequired()
                harness.signOutOthers.signOutOthers(ended) shouldBe DeviceOutcome.Failed.SessionExpired()
            }
        },
    )
