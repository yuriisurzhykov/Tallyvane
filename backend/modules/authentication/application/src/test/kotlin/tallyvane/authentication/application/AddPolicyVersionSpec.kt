package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.authentication.domain.Purpose
import tallyvane.platform.kernel.TransactionRunnerFake

private val GOOGLE = ProposedStep(listOf("google"), "always")
private val SECOND_IF_ENABLED = ProposedStep(listOf("totp", "recovery_code"), "when_enrolled")
private val SECOND_ALWAYS = ProposedStep(listOf("totp", "recovery_code"), "always")

private suspend fun PolicyDesk.numbers(purpose: String): List<String> =
    listed(list.list(ann, purpose)).filter { it.startsWith("number") }

class AddPolicyVersionSpec :
    StringSpec(
        {
            "a policy within the bounds is kept as the next version, and nothing else changes" {
                val desk = PolicyDesk()

                val outcome = desk.add.add(
                    desk.ann,
                    desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED), maxFailures = 3),
                    "Fewer tries",
                )

                outcome shouldBe PolicyVersionAdded.Added("login", 2)
                desk.numbers("login") shouldBe listOf("number 2", "number 1")
                desk.versions.active(Purpose.Login)?.let { NumberOf(it).number() } shouldBe 1
            }

            "the numbers of a purpose go on one after another, each purpose by itself" {
                val desk = PolicyDesk()
                val proposal = desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED))

                desk.add.add(desk.ann, proposal, "One")
                desk.add.add(desk.bob, proposal, "Two") shouldBe PolicyVersionAdded.Added("login", 3)
                desk.add.add(
                    desk.bob,
                    desk.proposal("step_up", listOf(GOOGLE, SECOND_IF_ENABLED)),
                    "Other purpose",
                ) shouldBe PolicyVersionAdded.Added("step_up", 2)
            }

            "everything wrong is told at once, so a form is corrected once" {
                val desk = PolicyDesk()

                val outcome = desk.add.add(
                    desk.ann,
                    desk.proposal(
                        "login",
                        listOf(GOOGLE, SECOND_IF_ENABLED),
                        attemptLifetimeMillis = 1,
                        maxFailures = 500,
                        firstDelayMillis = 10_000_000,
                    ),
                    "",
                ) as PolicyVersionAdded.Failed.Invalid

                desk.faults(outcome::writeTo) shouldBe listOf(
                    "attempt_lifetime_millis out-of-bounds",
                    "max_failures out-of-bounds",
                    "first_delay_millis out-of-bounds",
                    "comment comment-invalid",
                )
                desk.numbers("login") shouldBe listOf("number 1")
            }

            "words the API has no meaning for are told with the field that held them" {
                val desk = PolicyDesk()

                val outcome = desk.add.add(
                    desk.ann,
                    desk.proposal(
                        "login",
                        listOf(GOOGLE, ProposedStep(listOf("sms", "totp"), "sometimes")),
                    ),
                    "Why not",
                ) as PolicyVersionAdded.Failed.Invalid

                desk.faults(outcome::writeTo) shouldBe listOf("steps[1].accepts unknown", "steps[1].necessity unknown")
            }

            "an unknown purpose is a fault of the purpose" {
                val desk = PolicyDesk()

                val outcome = desk.add.add(desk.ann, desk.proposal("everything", listOf(GOOGLE)), "Why") as
                    PolicyVersionAdded.Failed.Invalid

                desk.faults(outcome::writeTo) shouldBe listOf("purpose unknown")
            }

            "a policy that demands a second factor of everyone would lock people out and is refused" {
                val desk = PolicyDesk()

                val outcome = desk.add.add(
                    desk.ann,
                    desk.proposal("login", listOf(GOOGLE, SECOND_ALWAYS)),
                    "Tighten",
                ) as PolicyVersionAdded.Failed.Invalid

                desk.faults(outcome::writeTo) shouldBe listOf("steps would-lock-people-out")
                desk.numbers("login") shouldBe listOf("number 1")
            }

            "the administrators' sign-in may demand the second factor of everyone, that is its floor" {
                val desk = PolicyDesk()

                desk.add.add(
                    desk.ann,
                    desk.proposal("admin_login", listOf(GOOGLE, SECOND_ALWAYS), maxFailures = 3),
                    "Fewer tries",
                ) shouldBe PolicyVersionAdded.Added("admin_login", 2)
            }

            "a policy that drops what a purpose always demanded is refused" {
                val desk = PolicyDesk()

                val outcome = desk.add.add(
                    desk.ann,
                    desk.proposal("admin_login", listOf(GOOGLE, SECOND_IF_ENABLED)),
                    "Loosen",
                ) as PolicyVersionAdded.Failed.Invalid

                desk.faults(outcome::writeTo) shouldBe listOf("steps below-the-floor")
            }

            "a comment is kept as the person wrote it, without the spaces around it" {
                val desk = PolicyDesk()
                desk.add.add(desk.ann, desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED)), "  Spaces  ")

                desk.listed(desk.list.list(desk.ann, "login"))
                    .first() shouldBe "kept by ${desk.ann.value} because 'Spaces'"
            }

            "a comment of more than 500 characters or with a control character is refused" {
                val desk = PolicyDesk()
                val proposal = desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED))

                listOf("x".repeat(501), "line one\nline two", "   ").forEach { comment ->
                    val outcome = desk.add.add(desk.ann, proposal, comment) as PolicyVersionAdded.Failed.Invalid
                    desk.faults(outcome::writeTo) shouldBe listOf("comment comment-invalid")
                }
                desk.add.add(desk.ann, proposal, "x".repeat(500)) shouldBe PolicyVersionAdded.Added("login", 2)
            }

            "someone without the right keeps nothing" {
                val desk = PolicyDesk()

                desk.add.add(
                    desk.cal,
                    desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED)),
                    "Let me in",
                ) shouldBe PolicyVersionAdded.Failed.NotAnAdministrator()

                desk.numbers("login") shouldBe listOf("number 1")
            }

            "a refusal rolls the transaction back and a version commits it" {
                val desk = PolicyDesk()
                val proposal = desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED))

                desk.add.add(desk.cal, proposal, "No")
                desk.add.add(desk.ann, proposal, "Yes")

                desk.transactions.endings.takeLast(2) shouldBe listOf(
                    TransactionRunnerFake.Ending.RolledBack,
                    TransactionRunnerFake.Ending.Committed,
                )
            }
        },
    )
