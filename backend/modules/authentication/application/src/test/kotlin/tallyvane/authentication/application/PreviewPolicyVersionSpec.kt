package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private val GOOGLE = ProposedStep(listOf("google"), "always")
private val SECOND_IF_ENABLED = ProposedStep(listOf("totp", "recovery_code"), "when_enrolled")
private val SECOND_ALWAYS = ProposedStep(listOf("totp", "recovery_code"), "always")
private val TOTP_ALWAYS = ProposedStep(listOf("totp"), "always")

class PreviewPolicyVersionSpec :
    StringSpec(
        {
            "a policy for everybody reaches every account and shuts out nobody who can sign in today" {
                val desk = PolicyDesk()
                listOf("a", "b", "c").forEach(desk.accounts::knows)
                desk.add.add(desk.ann, desk.proposal("login", listOf(GOOGLE, SECOND_IF_ENABLED)), "Same again")

                desk.previewed(desk.preview.preview(desk.ann, "login", 2)) shouldBe (3 to 0)
            }

            "for the administrators' sign-in the people are the administrators, and those with no factor are shut out" {
                val desk = PolicyDesk()
                desk.withTotp(desk.ann)

                desk.previewed(desk.preview.preview(desk.ann, "admin_login", 1)) shouldBe (2 to 1)
            }

            "a policy that accepts only TOTP shuts out an administrator who has only recovery codes left" {
                val desk = PolicyDesk()
                desk.withTotp(desk.ann)
                desk.withRecoveryCodesOnly(desk.bob)
                desk.add.add(desk.ann, desk.proposal("admin_login", listOf(GOOGLE, TOTP_ALWAYS)), "TOTP only")

                desk.previewed(desk.preview.preview(desk.ann, "admin_login", 1)) shouldBe (2 to 0)
                desk.previewed(desk.preview.preview(desk.ann, "admin_login", 2)) shouldBe (2 to 1)
            }

            "the preview of a rollback is the preview of the earlier version" {
                val desk = PolicyDesk()
                desk.withTotp(desk.ann)
                desk.withTotp(desk.bob)
                desk.add.add(desk.ann, desk.proposal("admin_login", listOf(GOOGLE, SECOND_ALWAYS)), "Same")

                desk.previewed(desk.preview.preview(desk.bob, "admin_login", 1)) shouldBe (2 to 0)
                desk.previewed(desk.preview.preview(desk.bob, "admin_login", 2)) shouldBe (2 to 0)
            }

            "a preview writes nothing: no version, no activation, and the transaction is not asked to commit it" {
                val desk = PolicyDesk()
                val before = desk.listed(desk.list.list(desk.ann, "login"))

                desk.preview.preview(desk.ann, "login", 1)

                desk.listed(desk.list.list(desk.ann, "login")) shouldBe before
            }

            "a number no version has is told, and so is a purpose nobody has" {
                val desk = PolicyDesk()

                desk.preview.preview(desk.ann, "login", 9) shouldBe PolicyVersionPreviewed.Failed.NoSuchVersion()
                val outcome = desk.preview.preview(desk.ann, "everything", 1) as PolicyVersionPreviewed.Failed.Invalid
                desk.faults(outcome::writeTo) shouldBe listOf("purpose unknown")
            }

            "someone without the right is refused" {
                val desk = PolicyDesk()

                desk.preview.preview(desk.cal, "login", 1) shouldBe PolicyVersionPreviewed.Failed.NotAnAdministrator()
            }
        },
    )
