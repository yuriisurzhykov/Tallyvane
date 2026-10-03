package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.contract.AccountId

private fun told(shown: SecondFactorShown): String = shown.reportTo(
    object : SecondFactorShown.Report<String> {
        override fun off(): String = "off"

        override fun active(recoveryCodesLeft: Int): String = "active, $recoveryCodesLeft codes"

        override fun retired(recoveryCodesLeft: Int): String = "retired, $recoveryCodesLeft codes"
    },
)

private fun told(verification: Verification): String = when (verification) {
    is Verification.Verified -> verification.reportTo(
        object : Verification.Verified.Report<String> {
            override fun totp(): String = "verified by code"

            override fun recovery(remaining: Int): String = "verified by recovery code, $remaining left"
        },
    )

    is Verification.Failed -> verification.toString()
}

private fun keyOf(begun: TotpBegun): String {
    val keys = mutableListOf<String>()
    (begun as TotpBegun.Started).writeTo { key, _ -> keys += key.revealed() }
    return keys.single()
}

private fun codesOf(regenerated: CodesRegenerated): List<String> {
    val shown = mutableListOf<String>()
    (regenerated as CodesRegenerated.Regenerated).writeTo { codes -> shown += codes.map { it.revealed() } }
    return shown
}

private suspend fun withAccount(body: suspend (Harness, AccountId) -> Unit) {
    val harness = Harness()
    harness.accounts.knows("sub-1")
    body(harness, checkNotNull(harness.accounts.withGoogle("sub-1")))
}

class TotpManagementSpec :
    StringSpec(
        {
            "a person who has not begun has nothing set up" {
                withAccount { harness, account ->
                    told(harness.showSecondFactor.show(account)) shouldBe "off"
                }
            }

            "beginning tells the key and an address that carries it, and protects nothing yet" {
                withAccount { harness, account ->
                    val uris = mutableListOf<String>()
                    val key = mutableListOf<String>()
                    (harness.beginTotp.begin(account) as TotpBegun.Started).writeTo { k, uri ->
                        key += k.revealed()
                        uris += uri.revealed()
                    }

                    uris.single().startsWith("otpauth://totp/Tallyvane?secret=${key.single()}") shouldBe true
                    told(harness.showSecondFactor.show(account)) shouldBe "off"
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

            "the first right code turns TOTP on and tells ten recovery codes once" {
                withAccount { harness, account ->
                    val app = AuthenticatorApp(keyOf(harness.beginTotp.begin(account)))
                    val shown = mutableListOf<String>()

                    (harness.confirmTotp.confirm(account, app.codeAt(harness.clock.now())) as TotpConfirmed.Confirmed)
                        .writeTo { codes -> shown += codes.map { it.revealed() } }

                    shown.size shouldBe 10
                    told(harness.showSecondFactor.show(account)) shouldBe "active, 10 codes"
                }
            }

            "a wrong first code changes nothing and may be tried again" {
                withAccount { harness, account ->
                    val app = AuthenticatorApp(keyOf(harness.beginTotp.begin(account)))

                    harness.confirmTotp.confirm(account, "000000") shouldBe TotpConfirmed.Failed.WrongCode()
                    told(harness.showSecondFactor.show(account)) shouldBe "off"

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

            "beginning while TOTP is on is refused and leaves it working" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")

                    harness.beginTotp.begin(account) shouldBe TotpBegun.Failed.AlreadyActive()

                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.standing(attempt) shouldBe "awaiting [RecoveryCode, Totp]"
                    setup.app.codeAt(harness.clock.now()).length shouldBe 6
                }
            }

            "new recovery codes replace the whole set, spent or not" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")

                    val fresh = codesOf(harness.regenerateRecoveryCodes.regenerate(account))

                    fresh.size shouldBe 10
                    fresh.intersect(setup.recoveryCodes.toSet()) shouldBe emptySet()
                    told(harness.showSecondFactor.show(account)) shouldBe "active, 10 codes"

                    val attempt = harness.signedInWithGoogle("sub-1")
                    told(harness.verify.verify(attempt, Submission.RecoveryCode(setup.recoveryCodes.first()))) shouldBe
                        "WrongCode(1s)"
                }
            }

            "new recovery codes need TOTP to be on" {
                withAccount { harness, account ->
                    harness.regenerateRecoveryCodes.regenerate(account) shouldBe CodesRegenerated.Failed.NotActive()

                    harness.beginTotp.begin(account)
                    harness.regenerateRecoveryCodes.regenerate(account) shouldBe CodesRegenerated.Failed.NotActive()
                }
            }

            "turning TOTP off removes the seed and the recovery codes, so Google alone signs in again" {
                withAccount { harness, account ->
                    harness.enableTotp("sub-1")

                    harness.disableTotp.disable(account) shouldBe TotpDisabled.Disabled()

                    told(harness.showSecondFactor.show(account)) shouldBe "off"
                    harness.secondFactors.of(account) shouldBe null
                    val attempt = harness.signedInWithGoogle("sub-1")
                    harness.standing(attempt) shouldBe "complete [Google]"
                }
            }

            "turning off what is not on is refused, and a begun enrolment can be abandoned" {
                withAccount { harness, account ->
                    harness.disableTotp.disable(account) shouldBe TotpDisabled.Failed.NotEnabled()

                    harness.beginTotp.begin(account)
                    harness.disableTotp.disable(account) shouldBe TotpDisabled.Disabled()
                }
            }

            "a recovery code retires the seed, which then shows as retired until it is turned on again" {
                withAccount { harness, account ->
                    val setup = harness.enableTotp("sub-1")
                    val attempt = harness.signedInWithGoogle("sub-1")

                    told(harness.verify.verify(attempt, Submission.RecoveryCode(setup.recoveryCodes.first()))) shouldBe
                        "verified by recovery code, 9 left"

                    told(harness.showSecondFactor.show(account)) shouldBe "retired, 9 codes"
                    (harness.beginTotp.begin(account) is TotpBegun.Started) shouldBe true
                }
            }
        },
    )
