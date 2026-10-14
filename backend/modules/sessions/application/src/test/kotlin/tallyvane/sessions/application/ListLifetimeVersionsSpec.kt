package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val SUPPORT = LifetimeSupport()

private fun Harness.administrator() = Harness.ACCOUNT.also { admins.grant(it) }

class ListLifetimeVersionsSpec :
    StringSpec(
        {
            "an administrator reads the versions of a kind of client newest first, with who made each and why" {
                val harness = Harness()
                val ann = harness.administrator()
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR), "  Tighter  ")

                SUPPORT.listed(harness.listLifetimeVersions.list(ann, "browser")) shouldBe listOf(
                    "kept by ${ann.value} because 'Tighter'",
                    "number 2",
                    "lifetimes ${1.hours} ${7.days} ${5.minutes}",
                    "kept by null because 'null'",
                    "in force since 2026-10-01T09:00:00Z by null because 'null'",
                    "number 1",
                    "lifetimes ${1.days} ${7.days} ${5.minutes}",
                )
            }

            "the administrators' own lifetimes start as version 1, in force, made by nobody" {
                val harness = Harness()
                val ann = harness.administrator()

                SUPPORT.listed(harness.listLifetimeVersions.list(ann, "admin")) shouldBe listOf(
                    "kept by null because 'null'",
                    "in force since 2026-10-01T09:00:00Z by null because 'null'",
                    "number 1",
                    "lifetimes ${1.hours} ${8.hours} ${5.minutes}",
                )
            }

            "someone without the right is refused, and so is someone who lost it a moment ago" {
                val harness = Harness()
                val ann = harness.administrator()

                harness.listLifetimeVersions.list(Harness.OTHER_ACCOUNT, "browser") shouldBe
                    LifetimeVersionsListed.Failed.NotAnAdministrator()

                harness.admins.revoke(ann)
                harness.listLifetimeVersions.list(ann, "browser") shouldBe
                    LifetimeVersionsListed.Failed.NotAnAdministrator()
            }

            "a word that names no kind of client is told as a fault of the client" {
                val harness = Harness()
                val ann = harness.administrator()

                val outcome = harness.listLifetimeVersions.list(ann, "toaster") as
                    LifetimeVersionsListed.Failed.Invalid

                SUPPORT.faults(outcome::writeTo) shouldBe listOf("client unknown")
            }
        },
    )
