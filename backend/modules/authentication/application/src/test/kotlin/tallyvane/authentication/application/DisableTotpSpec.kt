package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
class DisableTotpSpec :
    StringSpec(
        {
            "turning TOTP off removes the seed and the recovery codes, so Google alone signs in again" {
                withAccount { harness, account ->
                    harness.enableTotp("sub-1")

                    harness.disableTotp.disable(account, harness.session) shouldBe TotpDisabled.Disabled()

                    harness.journal.told().last() shouldBe "totpTurnedOff $account ${harness.session}"

                    shownAs(harness.showSecondFactor.show(account)) shouldBe "off"
                    harness.secondFactors.of(account) shouldBe null
                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.standing(attempt) shouldBe "complete [Google]"
                }
            }

            "turning off what is not on is refused, and a begun enrolment can be abandoned" {
                withAccount { harness, account ->
                    harness.disableTotp.disable(account, harness.session) shouldBe TotpDisabled.Failed.NotEnabled()
                    harness.journal.told() shouldBe emptyList()

                    harness.beginTotp.begin(account)
                    harness.disableTotp.disable(account, harness.session) shouldBe TotpDisabled.Disabled()
                }
            }
        },
    )
