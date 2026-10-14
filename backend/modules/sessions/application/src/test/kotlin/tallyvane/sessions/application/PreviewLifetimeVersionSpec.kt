package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.contract.AccountId
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private val SUPPORT = LifetimeSupport()

private const val QUARTER_OF_AN_HOUR = 900_000L

private val BOB = AccountId(Uuid.parse("0199a000-0000-7000-8000-0000000000cc"))

private fun Harness.administrator() = Harness.ACCOUNT.also { admins.grant(it) }

class PreviewLifetimeVersionSpec :
    StringSpec(
        {
            "a tighter version says how many live sessions it would end" {
                val harness = Harness()
                val ann = harness.administrator()
                harness.signedIn(Harness.ACCOUNT)
                harness.signedIn(Harness.OTHER_ACCOUNT)
                harness.clock.advance(2.hours)
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR), "Tighter")

                SUPPORT.previewed(harness.previewLifetimeVersion.preview(ann, "browser", 2)) shouldBe (2 to 2)
            }

            "a looser version, and the version in force, would end none" {
                val harness = Harness()
                val ann = harness.administrator()
                harness.signedIn(Harness.ACCOUNT)
                harness.addLifetimeVersion.add(
                    ann,
                    SUPPORT.proposal(idleMillis = 3 * LifetimeSupport.DAY, absoluteMillis = 30 * LifetimeSupport.DAY),
                    "Looser",
                )

                SUPPORT.previewed(harness.previewLifetimeVersion.preview(ann, "browser", 2)) shouldBe (1 to 0)
                SUPPORT.previewed(harness.previewLifetimeVersion.preview(ann, "browser", 1)) shouldBe (1 to 0)
            }

            "sessions that are over already are not counted among those that would end" {
                val harness = Harness()
                val ann = harness.administrator()
                harness.signedIn(Harness.ACCOUNT)
                harness.clock.advance(2.days)
                harness.signedIn(Harness.OTHER_ACCOUNT)
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR), "Tighter")
                harness.clock.advance(30.minutes)

                SUPPORT.previewed(harness.previewLifetimeVersion.preview(ann, "browser", 2)) shouldBe (1 to 0)
            }

            "only the sessions of the kind of client the version is for are counted" {
                val harness = Harness()
                val ann = harness.administrator()
                harness.signedIn(Harness.OTHER_ACCOUNT)
                harness.signedInAsAdmin(BOB)
                harness.clock.advance(30.minutes)
                harness.addLifetimeVersion.add(
                    ann,
                    SUPPORT.proposal("admin", idleMillis = QUARTER_OF_AN_HOUR, absoluteMillis = LifetimeSupport.HOUR),
                    "Tighter",
                )

                SUPPORT.previewed(harness.previewLifetimeVersion.preview(ann, "admin", 2)) shouldBe (1 to 1)
            }

            "a preview writes nothing and the rules in force stay as they were" {
                val harness = Harness()
                val ann = harness.administrator()
                val session = harness.signedIn(Harness.OTHER_ACCOUNT)
                harness.addLifetimeVersion.add(ann, SUPPORT.proposal(idleMillis = LifetimeSupport.HOUR), "Tighter")
                val before = SUPPORT.listed(harness.listLifetimeVersions.list(ann, "browser"))

                harness.previewLifetimeVersion.preview(ann, "browser", 2)
                harness.clock.advance(2.hours)

                SUPPORT.listed(harness.listLifetimeVersions.list(ann, "browser")) shouldBe before
                harness.who(session) shouldBe "signed in ${Harness.OTHER_ACCOUNT.value}"
            }

            "a number no version has is told, and so is a kind of client nobody has" {
                val harness = Harness()
                val ann = harness.administrator()

                harness.previewLifetimeVersion.preview(ann, "browser", 9) shouldBe
                    LifetimeVersionPreviewed.Failed.NoSuchVersion()
                val outcome = harness.previewLifetimeVersion.preview(ann, "toaster", 1) as
                    LifetimeVersionPreviewed.Failed.Invalid
                SUPPORT.faults(outcome::writeTo) shouldBe listOf("client unknown")
            }

            "someone without the right is refused" {
                val harness = Harness()

                harness.previewLifetimeVersion.preview(Harness.OTHER_ACCOUNT, "browser", 1) shouldBe
                    LifetimeVersionPreviewed.Failed.NotAnAdministrator()
            }
        },
    )
