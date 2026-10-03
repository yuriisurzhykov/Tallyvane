package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret
import tallyvane.sessions.domain.SessionId
import kotlin.uuid.Uuid

class RevokeDeviceSpec :
    StringSpec(
        {
            "signs out on another device, which then lapses while the one in use stays" {
                val harness = Harness()
                val here = harness.signedIn()
                val there = harness.signedIn(agent = MAC_CHROME)

                harness.revokeDevice.revoke(here, harness.idOfOther(here)) shouldBe DeviceOutcome.Done()

                harness.who(there) shouldBe "lapsed"
                harness.who(here) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }

            "signs out on the device in use when that one is named" {
                val harness = Harness()
                val here = harness.signedIn()
                val id = harness.shown(here).single().id

                harness.revokeDevice.revoke(here, id) shouldBe DeviceOutcome.Done()

                harness.who(here) shouldBe "lapsed"
            }

            "cannot sign out a stranger's device, and the answer is the same as for one that is not there" {
                val harness = Harness()
                val mine = harness.signedIn()
                val strangers = harness.signedIn(account = Harness.OTHER_ACCOUNT)
                val strangersId = harness.shown(strangers).single().id

                harness.revokeDevice.revoke(mine, strangersId) shouldBe DeviceOutcome.Failed.NoSuchDevice()

                harness.who(strangers) shouldBe "signed in ${Harness.OTHER_ACCOUNT.value}"
            }

            "refuses a request with no session or an ended one" {
                val harness = Harness()
                val id = SessionId(Uuid.parse("0199a000-0000-7000-8000-0000000000ff"))
                val ended = Secret("never-issued")

                harness.revokeDevice.revoke(null, id) shouldBe DeviceOutcome.Failed.SignInRequired()
                harness.revokeDevice.revoke(ended, id) shouldBe DeviceOutcome.Failed.SessionExpired()
            }
        },
    )
