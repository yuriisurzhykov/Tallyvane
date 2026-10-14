package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

private val GOOGLE = ProposedStep(listOf("google"), "always")
private val SECOND_IF_ENABLED = ProposedStep(listOf("totp", "recovery_code"), "when_enrolled")

private const val SEEDED_BY = "00000000-0000-4000-8000-0000000000ad"
private const val SEEDED_WHY = "A reason given in a test"

class ListPolicyVersionsSpec :
    StringSpec(
        {
            "an administrator reads the versions of a purpose newest first, with who made each and why" {
                val desk = PolicyDesk()
                desk.after(1.minutes)
                desk.add.add(
                    desk.ann,
                    desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED)),
                    "  Two attempts, no more  ",
                )

                desk.listed(desk.list.list(desk.bob, "login")) shouldBe listOf(
                    "kept by ${desk.ann.value} because 'Two attempts, no more'",
                    "number 2",
                    "kept by $SEEDED_BY because '$SEEDED_WHY'",
                    "in force since ${desk.clock.now() - 1.minutes} by $SEEDED_BY because '$SEEDED_WHY'",
                    "number 1",
                )
            }

            "the version in force is marked, and it moves when another is activated" {
                val desk = PolicyDesk()
                desk.add.add(
                    desk.ann,
                    desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED)),
                    "Same again",
                )
                desk.activate.activate(desk.bob, "login", number = 2, comment = "Go")

                desk.listed(desk.list.list(desk.ann, "login")) shouldBe listOf(
                    "kept by ${desk.ann.value} because 'Same again'",
                    "in force since ${desk.clock.now()} by ${desk.bob.value} because 'Go'",
                    "number 2",
                    "kept by $SEEDED_BY because '$SEEDED_WHY'",
                    "number 1",
                )
            }

            "a purpose with a version of its own is not mixed with the others" {
                val desk = PolicyDesk()

                desk.listed(desk.list.list(desk.ann, "step_up")).count { it.startsWith("number") } shouldBe 1
            }

            "someone without the right is refused, and so is someone who lost it a moment ago" {
                val desk = PolicyDesk()

                desk.list.list(desk.cal, "login") shouldBe PolicyVersionsListed.Failed.NotAnAdministrator()

                desk.admins.revoke(desk.bob)
                desk.list.list(desk.bob, "login") shouldBe PolicyVersionsListed.Failed.NotAnAdministrator()
            }

            "a word that names no purpose is told as a fault of the purpose" {
                val desk = PolicyDesk()

                val outcome = desk.list.list(desk.ann, "everything") as PolicyVersionsListed.Failed.Invalid

                desk.faults(outcome::writeTo) shouldBe listOf("purpose unknown")
            }
        },
    )
