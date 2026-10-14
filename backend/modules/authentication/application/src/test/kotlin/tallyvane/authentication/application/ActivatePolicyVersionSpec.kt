package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.domain.Purpose
import kotlin.time.Duration.Companion.minutes

private val GOOGLE = ProposedStep(listOf("google"), "always")
private val SECOND_IF_ENABLED = ProposedStep(listOf("totp", "recovery_code"), "when_enrolled")
private val SECOND_ALWAYS = ProposedStep(listOf("totp", "recovery_code"), "always")
private val TOTP_ALWAYS = ProposedStep(listOf("totp"), "always")

private suspend fun PolicyDesk.marks(purpose: String): List<String> =
    listed(list.list(ann, purpose)).filter { it.startsWith("in force") || it.startsWith("number") }

private fun PolicyDesk.numberInForce(purpose: Purpose): Int? = versions.active(purpose)?.let { NumberOf(it).number() }

class ActivatePolicyVersionSpec :
    StringSpec(
        {
            "a kept version is put in force with who did it and why, and the listing says so" {
                val desk = PolicyDesk()
                desk.add.add(
                    desk.ann,
                    desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED), maxFailures = 3),
                    "Three",
                )
                desk.after(5.minutes)

                desk.activate.activate(desk.bob, "login", 2, "  Three is enough ") shouldBe
                    PolicyVersionActivated.Activated()

                desk.numberInForce(Purpose.Login) shouldBe 2
                desk.marks("login") shouldBe listOf(
                    "in force since ${desk.clock.now()} by ${desk.bob.value} because 'Three is enough'",
                    "number 2",
                    "number 1",
                )
            }

            "rolling back is activating an earlier version, and the rollback is kept like any other" {
                val desk = PolicyDesk()
                desk.add.add(desk.ann, desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED)), "Two")
                desk.activate.activate(desk.ann, "login", 2, "Forward")
                desk.after(1.minutes)

                desk.activate.activate(desk.bob, "login", 1, "Back again") shouldBe PolicyVersionActivated.Activated()

                desk.numberInForce(Purpose.Login) shouldBe 1
                desk.marks("login") shouldBe listOf(
                    "number 2",
                    "in force since ${desk.clock.now()} by ${desk.bob.value} because 'Back again'",
                    "number 1",
                )
            }

            "a version put in force changes nothing for the other purposes" {
                val desk = PolicyDesk()
                desk.add.add(desk.ann, desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED)), "Two")

                desk.activate.activate(desk.ann, "login", 2, "Go")

                Purpose.entries.filter { it != Purpose.Login }.forEach { desk.numberInForce(it) shouldBe 1 }
            }

            "a number no version has changes nothing and is told" {
                val desk = PolicyDesk()

                desk.activate.activate(desk.ann, "login", 9, "Go") shouldBe
                    PolicyVersionActivated.Failed.NoSuchVersion()

                desk.numberInForce(Purpose.Login) shouldBe 1
            }

            "a purpose nobody has and a missing comment are told together" {
                val desk = PolicyDesk()

                val outcome = desk.activate.activate(desk.ann, "everything", 1, " ") as
                    PolicyVersionActivated.Failed.Invalid

                desk.faults(outcome::writeTo) shouldBe listOf("purpose unknown", "comment comment-invalid")
            }

            "someone without the right changes nothing, and neither does someone who lost it a moment ago" {
                val desk = PolicyDesk()
                desk.add.add(desk.ann, desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED)), "Two")

                desk.activate.activate(desk.cal, "login", 2, "Go") shouldBe
                    PolicyVersionActivated.Failed.NotAnAdministrator()
                desk.admins.revoke(desk.bob)
                desk.activate.activate(desk.bob, "login", 2, "Go") shouldBe
                    PolicyVersionActivated.Failed.NotAnAdministrator()

                desk.numberInForce(Purpose.Login) shouldBe 1
            }

            // The one change that can lock everybody out of the place the changes are made.
            "a version of the administrators' sign-in that none of them could pass is not put in force" {
                val desk = PolicyDesk()
                desk.withRecoveryCodesOnly(desk.ann)
                desk.withRecoveryCodesOnly(desk.bob)
                desk.add.add(desk.ann, desk.proposal("admin_login", listOf(GOOGLE, TOTP_ALWAYS)), "TOTP only")

                desk.activate.activate(desk.ann, "admin_login", 2, "Go") shouldBe
                    PolicyVersionActivated.Failed.WouldLockOutEveryAdministrator()

                desk.numberInForce(Purpose.AdminLogin) shouldBe 1
            }

            "a version of the administrators' sign-in that at least one of them could pass is put in force" {
                val desk = PolicyDesk()
                desk.withTotp(desk.ann)
                desk.withRecoveryCodesOnly(desk.bob)
                desk.add.add(desk.ann, desk.proposal("admin_login", listOf(GOOGLE, TOTP_ALWAYS)), "TOTP only")

                desk.activate.activate(desk.ann, "admin_login", 2, "Go") shouldBe PolicyVersionActivated.Activated()

                desk.numberInForce(Purpose.AdminLogin) shouldBe 2
            }

            "an administrator without a second factor is not enough to refuse nor to allow: they count as shut out" {
                val desk = PolicyDesk()
                desk.add.add(desk.ann, desk.proposal("admin_login", listOf(GOOGLE, SECOND_ALWAYS)), "Same")

                desk.activate.activate(desk.ann, "admin_login", 2, "Go") shouldBe
                    PolicyVersionActivated.Failed.WouldLockOutEveryAdministrator()
            }

            "a version for the others is never refused for locking out administrators" {
                val desk = PolicyDesk()
                desk.add.add(desk.ann, desk.proposal("step_up", listOf(GOOGLE, SECOND_IF_ENABLED)), "Same")

                desk.activate.activate(desk.ann, "step_up", 2, "Go") shouldBe PolicyVersionActivated.Activated()
            }
        },
    )
