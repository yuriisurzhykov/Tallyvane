package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
class RegenerateRecoveryCodesSpec :
    StringSpec(
        {
            "new recovery codes replace the whole set, spent or not" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")

                    val fresh = codesOf(harness.regenerateRecoveryCodes.regenerate(account, harness.session))

                    fresh.size shouldBe 10
                    fresh.intersect(setup.recoveryCodes.toSet()) shouldBe emptySet()
                    shownAs(harness.showSecondFactor.show(account)) shouldBe "active, 10 codes"
                    harness.journal.told().last() shouldBe "recoveryCodesReissued $account ${harness.session}"

                    val attempt = harness.signedInWithGoogle("sub-1")
                    val answer = harness.verify.verify(attempt, Submission.RecoveryCode(setup.recoveryCodes.first()))
                    toldAs(answer) shouldBe "WrongCode(1s)"
                }
            }

            "new recovery codes need TOTP to be on" {
                withAccount { harness, account ->
                    harness.regenerateRecoveryCodes.regenerate(account, harness.session) shouldBe
                        CodesRegenerated.Failed.NotActive()
                    harness.journal.told() shouldBe emptyList()

                    harness.beginTotp.begin(account)
                    harness.regenerateRecoveryCodes.regenerate(account, harness.session) shouldBe
                        CodesRegenerated.Failed.NotActive()
                }
            }
        },
    )
