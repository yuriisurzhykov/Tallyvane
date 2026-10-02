package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret
import tallyvane.sessions.domain.Session
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AuthenticateSpec :
    StringSpec(
        {
            "a request with no session is anonymous" {
                Harness().who(null) shouldBe "anonymous"
            }

            "a secret nobody was given is a session that lapsed, not an anonymous request" {
                Harness().who(Secret("never-issued")) shouldBe "lapsed"
            }

            "a session speaks for its person on every request" {
                val harness = Harness()
                val session = harness.signedIn()

                harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
                harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }

            "a session left unused for as long as it may be is over" {
                val harness = Harness()
                val session = harness.signedIn()

                harness.clock.advance(1.days)

                harness.who(session) shouldBe "lapsed"
            }

            "a session left unused for a little less than that is still good" {
                val harness = Harness()
                val session = harness.signedIn()

                harness.clock.advance(1.days - 1.minutes)

                harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }

            "use keeps a session alive past the idle limit, which is counted from the last use" {
                val harness = Harness()
                val session = harness.signedIn()
                repeat(3) {
                    harness.clock.advance(20.hours)
                    harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
                }
            }

            "a session in daily use is still over once it has lived as long as it may" {
                val harness = Harness()
                val session = harness.signedIn()
                repeat(7) {
                    harness.clock.advance(1.days - 1.minutes)
                    harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
                }

                // Ten minutes since its last use, so only its age can end it.
                harness.clock.advance(10.minutes)

                harness.who(session) shouldBe "lapsed"
            }

            "a session that is over is forgotten, so it stays over" {
                val harness = Harness()
                val session = harness.signedIn()
                harness.clock.advance(8.days)
                harness.who(session)

                harness.sessions.count() shouldBe 0
            }

            "use is noted no more often than a grain, so a busy person does not make a write per request" {
                val harness = Harness()
                val session = harness.signedIn()
                val begun = harness.clock.now()

                harness.clock.advance(Session.USE_GRAIN / 2)
                harness.who(session)

                harness.sessions.lastUses().single() shouldBe begun
            }
        },
    )
