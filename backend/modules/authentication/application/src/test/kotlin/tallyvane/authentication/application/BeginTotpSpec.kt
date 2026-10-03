package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
class BeginTotpSpec :
    StringSpec(
        {
            "beginning tells the key and an address that carries it, and protects nothing yet" {
                withAccount { harness, account ->
                    val uris = mutableListOf<String>()
                    val key = mutableListOf<String>()
                    (harness.beginTotp.begin(account) as TotpBegun.Started).writeTo { k, uri ->
                        key += k.revealed()
                        uris += uri.revealed()
                    }

                    uris.single().startsWith("otpauth://totp/Tallyvane?secret=${key.single()}") shouldBe true
                    shownAs(harness.showSecondFactor.show(account)) shouldBe "off"
                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.standing(attempt) shouldBe "complete [Google]"
                }
            }

            "beginning again starts over with another key" {
                withAccount { harness, account ->
                    val first = keyOf(harness.beginTotp.begin(account))
                    val second = keyOf(harness.beginTotp.begin(account))

                    (first == second) shouldBe false
                }
            }

            "beginning while TOTP is on is refused and leaves it working" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")

                    harness.beginTotp.begin(account) shouldBe TotpBegun.Failed.AlreadyActive()

                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.standing(attempt) shouldBe "awaiting [RecoveryCode, Totp]"
                    setup.app.codeAt(harness.clock.now()).length shouldBe 6
                }
            }
        },
    )
