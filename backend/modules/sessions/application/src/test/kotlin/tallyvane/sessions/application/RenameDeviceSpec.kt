package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret
import tallyvane.sessions.domain.SessionId
import kotlin.uuid.Uuid

class RenameDeviceSpec :
    StringSpec(
        {
            "names a device, and the list shows the name" {
                val harness = Harness()
                val here = harness.signedIn()
                val id = harness.shown(here).single().id

                harness.renameDevice.rename(here, id, "  Work laptop ") shouldBe DeviceOutcome.Done()

                harness.shown(here).single().name shouldBe "Work laptop"
            }

            "refuses a name that is empty or too long, and changes nothing" {
                val harness = Harness()
                val here = harness.signedIn()
                val id = harness.shown(here).single().id

                harness.renameDevice.rename(here, id, "   ") shouldBe DeviceOutcome.Failed.NameRefused()
                harness.renameDevice.rename(here, id, "x".repeat(61)) shouldBe DeviceOutcome.Failed.NameRefused()

                harness.shown(here).single().name shouldBe null
            }

            "cannot name a stranger's device" {
                val harness = Harness()
                val mine = harness.signedIn()
                val strangers = harness.signedIn(account = Harness.OTHER_ACCOUNT)
                val strangersId = harness.shown(strangers).single().id

                harness.renameDevice.rename(mine, strangersId, "Mine now") shouldBe DeviceOutcome.Failed.NoSuchDevice()

                harness.shown(strangers).single().name shouldBe null
            }

            "refuses a request with no session or an ended one" {
                val harness = Harness()
                val id = SessionId(Uuid.parse("0199a000-0000-7000-8000-0000000000ff"))
                val ended = Secret("never-issued")

                harness.renameDevice.rename(null, id, "x") shouldBe DeviceOutcome.Failed.SignInRequired()
                harness.renameDevice.rename(ended, id, "x") shouldBe DeviceOutcome.Failed.SessionExpired()
            }
        },
    )
