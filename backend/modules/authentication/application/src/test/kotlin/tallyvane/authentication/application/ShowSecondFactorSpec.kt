package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
class ShowSecondFactorSpec :
    StringSpec(
        {
            "a person who has not begun has nothing set up" {
                withAccount { harness, account ->
                    shownAs(harness.showSecondFactor.show(account)) shouldBe "off"
                }
            }

            "a recovery code retires the seed, which then shows as retired until it is turned on again" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")
                    val attempt = harness.signedInWithGoogle("sub-1")

                    val answer = harness.verify.verify(attempt, Submission.RecoveryCode(setup.recoveryCodes.first()))
                    toldAs(answer) shouldBe "verified by recovery code, 9 left"

                    shownAs(harness.showSecondFactor.show(account)) shouldBe "retired, 9 codes"
                    (harness.beginTotp.begin(account) is TotpBegun.Started) shouldBe true
                }
            }
        },
    )
