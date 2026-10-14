package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Duration.Companion.hours

private val SUPPORT = LifetimeSupport()

private fun Harness.administrator() = Harness.ACCOUNT.also { admins.grant(it) }

private suspend fun Harness.numbers(client: String) = SUPPORT.listed(
    listLifetimeVersions.list(administrator(), client),
).filter { it.startsWith("number") }

class AddLifetimeVersionSpec :
    StringSpec(
        {
            "lifetimes within the bounds are kept as the next version, and nothing else changes" {
                val harness = Harness()
                val ann = harness.administrator()

                harness.addLifetimeVersion.add(
                    ann,
                    SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR),
                    "Tighter",
                ) shouldBe
                    LifetimeVersionAdded.Added("browser", 2)

                harness.numbers("browser") shouldBe listOf("number 2", "number 1")
                val session = harness.signedIn()
                harness.clock.advance(2.hours)
                harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }

            "each kind of client has numbers of its own" {
                val harness = Harness()
                val ann = harness.administrator()

                harness.addLifetimeVersion.add(ann, SUPPORT.proposal("browser"), "One")
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal("admin"), "Other kind") shouldBe
                    LifetimeVersionAdded.Added("admin", 2)
            }

            "everything wrong is told at once, so a form is corrected once" {
                val harness = Harness()
                val ann = harness.administrator()

                val outcome = harness.addLifetimeVersion.add(
                    ann,
                    SUPPORT.proposal(idleMillis = 1, absoluteMillis = 0, freshnessMillis = 3_600_000),
                    "",
                ) as LifetimeVersionAdded.Failed.Invalid

                SUPPORT.faults(outcome::writeTo) shouldBe listOf(
                    "idle_millis out-of-bounds",
                    "absolute_millis below-idle",
                    "freshness_millis out-of-bounds",
                    "comment comment-invalid",
                )
                harness.numbers("browser") shouldBe listOf("number 1")
            }

            "an absolute limit over 90 days is told as out of bounds" {
                val harness = Harness()
                val ann = harness.administrator()

                val outcome = harness.addLifetimeVersion.add(
                    ann,
                    SUPPORT.proposal(absoluteMillis = 91 * LifetimeSupport.DAY),
                    "Forever",
                ) as LifetimeVersionAdded.Failed.Invalid

                SUPPORT.faults(outcome::writeTo) shouldBe listOf("absolute_millis out-of-bounds")
            }

            "a kind of client the API has no word for is a fault of the client" {
                val harness = Harness()
                val ann = harness.administrator()

                val outcome = harness.addLifetimeVersion.add(ann, SUPPORT.proposal("toaster"), "Why") as
                    LifetimeVersionAdded.Failed.Invalid

                SUPPORT.faults(outcome::writeTo) shouldBe listOf("client unknown")
            }

            "a comment of more than 500 characters or with a control character is refused" {
                val harness = Harness()
                val ann = harness.administrator()

                listOf("x".repeat(501), "one\ntwo", "  ").forEach { comment ->
                    val outcome = harness.addLifetimeVersion.add(ann, SUPPORT.proposal(), comment) as
                        LifetimeVersionAdded.Failed.Invalid
                    SUPPORT.faults(outcome::writeTo) shouldBe listOf("comment comment-invalid")
                }
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(), "x".repeat(500)) shouldBe
                    LifetimeVersionAdded.Added("browser", 2)
            }

            "someone without the right keeps nothing" {
                val harness = Harness()
                harness.administrator()

                harness.addLifetimeVersion.add(Harness.OTHER_ACCOUNT, SUPPORT.proposal(), "Let me in") shouldBe
                    LifetimeVersionAdded.Failed.NotAnAdministrator()

                harness.numbers("browser") shouldBe listOf("number 1")
            }

            "a refusal rolls the transaction back and a version commits it" {
                val harness = Harness()
                val ann = harness.administrator()

                harness.addLifetimeVersion.add(Harness.OTHER_ACCOUNT, SUPPORT.proposal(), "No")
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(), "Yes")

                harness.endings().takeLast(2) shouldBe listOf(
                    TransactionRunnerFake.Ending.RolledBack,
                    TransactionRunnerFake.Ending.Committed,
                )
            }
        },
    )
