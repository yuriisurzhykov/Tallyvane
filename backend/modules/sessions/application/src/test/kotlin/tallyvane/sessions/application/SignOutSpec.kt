package tallyvane.sessions.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.platform.kernel.Secret

class SignOutSpec :
    StringSpec(
        {
            "a session that signed out is a stranger's from then on" {
                val harness = Harness()
                val session = harness.signedIn()

                harness.signOut.signOut(session)

                harness.who(session) shouldBe "lapsed"
            }

            "signing out ends only that session" {
                val harness = Harness()
                val first = harness.signedIn()
                val second = harness.signedIn()

                harness.signOut.signOut(first)

                harness.who(second) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }

            "signing out with no session, or one nobody issued, changes nothing and is no failure" {
                val harness = Harness()
                val session = harness.signedIn()

                harness.signOut.signOut(null)
                harness.signOut.signOut(Secret("never-issued"))

                harness.who(session) shouldBe "signed in ${Harness.ACCOUNT.value}"
            }
        },
    )
