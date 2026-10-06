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

            "setting up again and walking away leaves the person retired, with their recovery codes" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")
                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.verify.verify(attempt, Submission.RecoveryCode(setup.recoveryCodes.first()))

                    (harness.beginTotp.begin(account) is TotpBegun.Started) shouldBe true

                    shownAs(harness.showSecondFactor.show(account)) shouldBe "retired, 9 codes"
                    val again = harness.signedInWithGoogle("sub-1")
                    harness.standing(again) shouldBe "awaiting [RecoveryCode]"
                }
            }

            "confirming the new seed after setting up again makes TOTP active with ten new codes" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")
                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.verify.verify(attempt, Submission.RecoveryCode(setup.recoveryCodes.first()))
                    val app = AuthenticatorApp(keyOf(harness.beginTotp.begin(account)))

                    val confirmed = harness.confirmTotp.confirm(account, app.codeAt(harness.clock.now()))
                    (confirmed is TotpConfirmed.Confirmed) shouldBe true

                    shownAs(harness.showSecondFactor.show(account)) shouldBe "active, 10 codes"
                }
            }

            "a person who set up again over a retired seed can still turn TOTP off" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")
                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.verify.verify(attempt, Submission.RecoveryCode(setup.recoveryCodes.first()))
                    harness.beginTotp.begin(account)

                    harness.disableTotp.disable(account) shouldBe TotpDisabled.Disabled()

                    shownAs(harness.showSecondFactor.show(account)) shouldBe "off"
                }
            }
        },
    )
