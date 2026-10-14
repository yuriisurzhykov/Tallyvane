package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val SUPPORT = LifetimeSupport()

private fun Harness.administrator() = Harness.ACCOUNT.also { admins.grant(it) }

class ActivateLifetimeVersionSpec :
    StringSpec(
        {
            "a kept version put in force reaches a session issued before it, at its next request" {
                val harness = Harness()
                val ann = harness.administrator()
                val session = harness.signedIn(Harness.OTHER_ACCOUNT)
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR), "Tighter")
                harness.clock.advance(2.hours)

                harness.activateLifetimeVersion.activate(ann, "browser", 2, "  Tighter, as agreed ") shouldBe
                    LifetimeVersionActivated.Activated()

                harness.who(session) shouldBe "lapsed"
            }

            "the listing says who put the version in force, from when, and why" {
                val harness = Harness()
                val ann = harness.administrator()
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR), "Tighter")
                harness.clock.advance(5.minutes)
                harness.activateLifetimeVersion.activate(ann, "browser", 2, "Go")

                SUPPORT.listed(harness.listLifetimeVersions.list(ann, "browser")).take(3) shouldBe listOf(
                    "kept by ${ann.value} because 'Tighter'",
                    "in force since ${harness.clock.now()} by ${ann.value} because 'Go'",
                    "number 2",
                )
            }

            "rolling back is activating an earlier version" {
                val harness = Harness()
                val ann = harness.administrator()
                val session = harness.signedIn(Harness.OTHER_ACCOUNT)
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR), "Tighter")
                harness.activateLifetimeVersion.activate(ann, "browser", 2, "Go")
                harness.clock.advance(2.hours)
                harness.who(session) shouldBe "lapsed"

                harness.activateLifetimeVersion.activate(ann, "browser", 1, "Back again")
                val another = harness.signedIn(Harness.ACCOUNT)
                harness.clock.advance(2.hours)

                harness.who(another) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }

            "a version of one kind of client does not change the other" {
                val harness = Harness()
                val ann = harness.administrator()
                harness.addLifetimeVersion.add(
                    ann,
                    SUPPORT.proposal(
                        "admin",
                        idleMillis = 3 * LifetimeSupport.HOUR,
                        absoluteMillis =
                        12 * LifetimeSupport.HOUR,
                    ),
                    "Longer",
                )
                harness.activateLifetimeVersion.activate(ann, "admin", 2, "Go")
                val session = harness.signedIn(Harness.OTHER_ACCOUNT)
                harness.clock.advance(23.hours)

                harness.who(session) shouldBe "signed in ${Harness.OTHER_ACCOUNT.value}"
                harness.clock.advance(2.days)
                harness.who(session) shouldBe "lapsed"
            }

            "a number no version has changes nothing and is told" {
                val harness = Harness()
                val ann = harness.administrator()

                harness.activateLifetimeVersion.activate(ann, "browser", 9, "Go") shouldBe
                    LifetimeVersionActivated.Failed.NoSuchVersion()
            }

            "a kind of client nobody has and a missing comment are told together" {
                val harness = Harness()
                val ann = harness.administrator()

                val outcome = harness.activateLifetimeVersion.activate(ann, "toaster", 1, " ") as
                    LifetimeVersionActivated.Failed.Invalid

                SUPPORT.faults(outcome::writeTo) shouldBe listOf("client unknown", "comment comment-invalid")
            }

            "someone without the right changes nothing, and neither does someone who lost it a moment ago" {
                val harness = Harness()
                val ann = harness.administrator()
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR), "Tighter")

                harness.activateLifetimeVersion.activate(Harness.OTHER_ACCOUNT, "browser", 2, "Go") shouldBe
                    LifetimeVersionActivated.Failed.NotAnAdministrator()
                harness.admins.revoke(ann)
                harness.activateLifetimeVersion.activate(ann, "browser", 2, "Go") shouldBe
                    LifetimeVersionActivated.Failed.NotAnAdministrator()

                SUPPORT.listed(harness.listLifetimeVersions.list(harness.administrator(), "browser")).count {
                    it.startsWith("in force since")
                } shouldBe 1
            }
        },
    )
