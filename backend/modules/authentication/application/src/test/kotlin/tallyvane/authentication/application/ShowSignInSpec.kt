package tallyvane.authentication.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private fun told(shown: SignInShown): String = when (shown) {
    is SignInShown.Shown -> shown.reportTo(
        object : SignInShown.Shown.Report<String> {
            override fun awaiting(totp: Boolean, recoveryCode: Boolean): String =
                "awaiting totp=$totp recovery=$recoveryCode"

            override fun paused(totp: Boolean, recoveryCode: Boolean, wait: Duration): String =
                "paused totp=$totp recovery=$recoveryCode for $wait"

            override fun complete(): String = "complete"

            override fun restricted(): String = "restricted"

            override fun exhausted(): String = "exhausted"

            override fun expired(): String = "expired"
        },
    )

    is SignInShown.Failed -> shown.toString()
}

class ShowSignInSpec :
    StringSpec(
        {
            "a browser with no cookie, or one nobody knows, has nothing to look at" {
                val harness = Harness()

                told(harness.showSignIn.show(null)) shouldBe "NoSignIn"
                told(harness.showSignIn.show(Secret("nobody-knows-this"))) shouldBe "NoSignIn"
            }

            "an account with no second factor is complete after Google" {
                val harness = Harness()
                harness.accounts.knows("sub-1")

                told(harness.showSignIn.show(harness.signedInWithGoogle("sub-1"))) shouldBe "complete"
            }

            "an account with TOTP on is waiting for a code or a recovery code after Google" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                harness.enableTotp("sub-1")

                told(harness.showSignIn.show(harness.signedInWithGoogle("sub-1"))) shouldBe
                    "awaiting totp=true recovery=true"
            }

            "a wrong code starts a pause that is told as the time left" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                harness.enableTotp("sub-1")
                val attempt = harness.signedInWithGoogle("sub-1")
                harness.verify.verify(attempt, Submission.TotpCode("000000"))

                told(harness.showSignIn.show(attempt)) shouldBe "paused totp=true recovery=true for 1s"
            }

            "an attempt past its lifetime is expired" {
                val harness = Harness()
                harness.accounts.knows("sub-1")
                harness.enableTotp("sub-1")
                val attempt = harness.signedInWithGoogle("sub-1")
                harness.clock.passes(6.minutes)

                told(harness.showSignIn.show(attempt)) shouldBe "expired"
            }
        },
    )
