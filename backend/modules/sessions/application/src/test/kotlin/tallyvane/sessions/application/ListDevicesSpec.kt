package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret
import tallyvane.sessions.domain.Browser
import tallyvane.sessions.domain.Platform
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class ListDevicesSpec :
    StringSpec(
        {
            "lists the devices a person is signed in on, the one in use marked, most recently used first" {
                val harness = Harness()
                val windows = harness.signedIn()
                harness.clock.advance(10.minutes)
                val mac = harness.signedIn(agent = MAC_CHROME)
                harness.clock.advance(5.minutes)

                harness.shown(mac).map { it.platform to it.current } shouldBe
                    listOf(Platform.MacOs to true, Platform.Windows to false)
                harness.shown(windows).map { it.platform to it.current } shouldBe
                    listOf(Platform.Windows to true, Platform.MacOs to false)
            }

            "tells Chrome on a Mac from Chrome on Windows in the list" {
                val harness = Harness()
                val windows = harness.signedIn()
                harness.signedIn(agent = MAC_CHROME)

                harness.shown(windows).map { it.browser to it.platform }.toSet() shouldBe
                    setOf(Browser.Chrome to Platform.Windows, Browser.Chrome to Platform.MacOs)
            }

            "lists only the person's own devices" {
                val harness = Harness()
                val mine = harness.signedIn()
                harness.signedIn(account = Harness.OTHER_ACCOUNT)

                harness.shown(mine).size shouldBe 1
            }

            "does not list a session that is over under the lifetimes in force, though nothing has forgotten it" {
                val harness = Harness()
                val old = harness.signedIn()
                harness.clock.advance(2.days)
                val fresh = harness.signedIn(agent = MAC_CHROME)

                harness.shown(fresh).map { it.platform } shouldBe listOf(Platform.MacOs)
                harness.sessions.count() shouldBe 2
                harness.who(old) shouldBe "lapsed"
            }

            "refuses to list for a request with no session, and for one whose session is over" {
                val harness = Harness()

                harness.listDevices.list(null) shouldBe DeviceOutcome.Failed.SignInRequired()
                harness.listDevices.list(Secret("never-issued")) shouldBe DeviceOutcome.Failed.SessionExpired()
            }

            "keeps the device the browser said it was when the session began" {
                val harness = Harness()
                val session = harness.signedIn(agent = MAC_CHROME)

                harness.shown(session).single().let { it.browser to it.platform } shouldBe
                    (Browser.Chrome to Platform.MacOs)
            }
        },
    )
