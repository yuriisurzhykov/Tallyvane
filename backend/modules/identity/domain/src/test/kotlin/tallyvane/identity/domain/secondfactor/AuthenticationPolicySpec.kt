package tallyvane.identity.domain.secondfactor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AuthenticationPolicySpec :
    StringSpec({
        "default email sign-in does not combine two proofs delivered to the same mailbox" {
            val emailSchemes = AuthenticationPolicy.defaults().schemesFor(AuthenticationAction.SIGN_IN)
                .filter { AuthenticationTokenKind.EMAIL_SIGN_IN_CODE in it.requiredTokens }
                .map { it.requiredTokens }
                .toSet()
            emailSchemes shouldBe setOf(
                setOf(AuthenticationTokenKind.EMAIL_SIGN_IN_CODE),
                setOf(AuthenticationTokenKind.EMAIL_SIGN_IN_CODE, AuthenticationTokenKind.TOTP),
            )
        }
        "email after email cannot be saved without explicit advanced acknowledgement" {
            val rule =
                AuthenticationRule(
                    PrimaryMethod.EMAIL_CODE,
                    true,
                    MfaRequirement.REQUIRED,
                    setOf(SecondFactorKind.EMAIL_OTP),
                )
            val defaults = AuthenticationPolicy.defaults()
            val rules = defaults.rules.values.filterNot { it.primary == PrimaryMethod.EMAIL_CODE } + rule
            shouldThrow<IllegalArgumentException> { AuthenticationPolicy(1, rules, false) }
            AuthenticationPolicy(1, rules, true).advancedAcknowledged shouldBe true
        }
        "cannot disable every primary method" {
            shouldThrow<IllegalArgumentException> {
                val defaults = AuthenticationPolicy.defaults()
                val rules = defaults.rules.values.map { it.copy(enabled = false) }
                AuthenticationPolicy(1, rules, false)
            }
        }
    })
