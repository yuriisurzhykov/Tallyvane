package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
class RegenerateRecoveryCodesSpec :
    StringSpec(
        {
            "new recovery codes replace the whole set, spent or not" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")

                    val fresh = codesOf(harness.regenerateRecoveryCodes.regenerate(account))

                    fresh.size shouldBe 10
                    fresh.intersect(setup.recoveryCodes.toSet()) shouldBe emptySet()
                    shownAs(harness.showSecondFactor.show(account)) shouldBe "active, 10 codes"

                    val attempt = harness.signedInWithGoogle("sub-1")
                    val answer = harness.verify.verify(attempt, Submission.RecoveryCode(setup.recoveryCodes.first()))
                    toldAs(answer) shouldBe "WrongCode(1s)"
                }
            }

            "new recovery codes need TOTP to be on" {
                withAccount { harness, account ->
                    harness.regenerateRecoveryCodes.regenerate(account) shouldBe CodesRegenerated.Failed.NotActive()

                    harness.beginTotp.begin(account)
                    harness.regenerateRecoveryCodes.regenerate(account) shouldBe CodesRegenerated.Failed.NotActive()
                }
            }
        },
    )
