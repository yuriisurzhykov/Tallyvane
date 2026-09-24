package tallyvane.identity.domain.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class AuthenticationPolicySpec : StringSpec({
    "default email sign-in requires an independent enrolled factor" {
        val rule = AuthenticationPolicy.defaults().rule(PrimaryMethod.EMAIL_CODE)
        rule.available(setOf(SecondFactorKind.TOTP, SecondFactorKind.EMAIL_OTP)) shouldBe setOf(SecondFactorKind.TOTP)
    }
    "required policy forces enrollment when no permitted factor exists" {
        val rule = AuthenticationRule(PrimaryMethod.PASSWORD, true, MfaRequirement.REQUIRED, setOf(SecondFactorKind.TOTP))
        rule.requiresEnrollment(emptySet()) shouldBe true
        rule.requiresEnrollment(setOf(SecondFactorKind.TOTP)) shouldBe false
    }
    "email after email cannot be saved without explicit advanced acknowledgement" {
        val rule = AuthenticationRule(PrimaryMethod.EMAIL_CODE, true, MfaRequirement.REQUIRED, setOf(SecondFactorKind.EMAIL_OTP))
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
