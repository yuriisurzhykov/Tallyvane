package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
class ConfirmTotpSpec :
    StringSpec(
        {
            "the first right code turns TOTP on and tells ten recovery codes once" {
                withAccount { harness, account ->
                    val app = AuthenticatorApp(keyOf(harness.beginTotp.begin(account)))
                    val shown = mutableListOf<String>()

                    (harness.confirmTotp.confirm(account, app.codeAt(harness.clock.now())) as TotpConfirmed.Confirmed)
                        .writeTo { codes -> shown += codes.map { it.revealed() } }

                    shown.size shouldBe 10
                    shownAs(harness.showSecondFactor.show(account)) shouldBe "active, 10 codes"
                }
            }

            "a wrong first code changes nothing and may be tried again" {
                withAccount { harness, account ->
                    val app = AuthenticatorApp(keyOf(harness.beginTotp.begin(account)))

                    harness.confirmTotp.confirm(account, "000000") shouldBe TotpConfirmed.Failed.WrongCode()
                    shownAs(harness.showSecondFactor.show(account)) shouldBe "off"

                    (
                        harness.confirmTotp.confirm(
                            account,
                            app.codeAt(harness.clock.now()),
                        ) is TotpConfirmed.Confirmed
                        ) shouldBe
                        true
                }
            }

            "confirming with nothing begun, or once it is on, is refused" {
                withAccount { harness, account ->
                    harness.confirmTotp.confirm(account, "123456") shouldBe TotpConfirmed.Failed.NotBegun()

                    val setup = harness.enableTotp("sub-1")
                    harness.confirmTotp.confirm(account, setup.app.codeAt(harness.clock.now())) shouldBe
                        TotpConfirmed.Failed.NotBegun()
                }
            }
        },
    )
