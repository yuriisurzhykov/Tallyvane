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

            "tells the journal, with the session that stays" {
                val harness = Harness()
                harness.signedIn()
                val here = harness.signedIn()
                harness.journal.told().size shouldBe 2

                harness.signOutOthers.signOutOthers(here)

                val line = harness.journal.told().last()
                line.startsWith("otherDevicesSignedOut ${Harness.ACCOUNT} ") shouldBe true
                // The session it names is the second one's, which the sign-in line names too.
                val named = line.removePrefix("otherDevicesSignedOut ${Harness.ACCOUNT} ")
                harness.journal.told()[1].contains(" $named ") shouldBe true
            }

            "refuses a request with no session or an ended one" {
                val harness = Harness()
                val ended = Secret("never-issued")

                harness.signOutOthers.signOutOthers(null) shouldBe DeviceOutcome.Failed.SignInRequired()
                harness.signOutOthers.signOutOthers(ended) shouldBe DeviceOutcome.Failed.SessionExpired()
                harness.journal.told() shouldBe emptyList()
            }
        },
    )
