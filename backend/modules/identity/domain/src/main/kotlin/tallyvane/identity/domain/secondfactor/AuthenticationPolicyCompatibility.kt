package tallyvane.identity.domain.secondfactor

/**
 * Maps the versioned scheme model to the legacy rule view used by older callers.
 */
internal class AuthenticationPolicyCompatibility {
    fun legacyRules(schemes: List<AuthenticationScheme>): Map<PrimaryMethod, AuthenticationRule> =
        PrimaryMethod.entries.associateWith { primary -> legacyRule(primary, schemes) }

    fun primaryToken(primary: PrimaryMethod): AuthenticationTokenKind = when (primary) {
        PrimaryMethod.PASSWORD -> AuthenticationTokenKind.PASSWORD
        PrimaryMethod.GOOGLE -> AuthenticationTokenKind.GOOGLE
        PrimaryMethod.EMAIL_CODE -> AuthenticationTokenKind.EMAIL_SIGN_IN_CODE
    }

    fun factorToken(factor: SecondFactorKind): AuthenticationTokenKind = when (factor) {
        SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
        SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
    }

    private fun legacyRule(primary: PrimaryMethod, schemes: List<AuthenticationScheme>): AuthenticationRule {
        val primaryToken = primaryToken(primary)
        val signIn = schemes.filter {
            it.enabled && it.action == AuthenticationAction.SIGN_IN && primaryToken in it.requiredTokens
        }
        val factors = signIn.flatMapTo(mutableSetOf()) { scheme ->
            scheme.requiredTokens
                .filter(AuthenticationTokenKind::isSecondFactor)
                .map(::secondFactor)
        }
        val hasCombinedScheme = signIn.any { scheme ->
            scheme.requiredTokens.any(AuthenticationTokenKind::isSecondFactor)
        }
        return AuthenticationRule(
            primary = primary,
            enabled = signIn.isNotEmpty(),
            requirement = if (hasCombinedScheme) MfaRequirement.IF_ENROLLED else MfaRequirement.DISABLED,
            allowedMethods = factors,
        )
    }

    private fun secondFactor(token: AuthenticationTokenKind): SecondFactorKind = when (token) {
        AuthenticationTokenKind.TOTP -> SecondFactorKind.TOTP
        AuthenticationTokenKind.EMAIL_FACTOR_CODE -> SecondFactorKind.EMAIL_OTP
        else -> error("$token is not a second-factor token")
    }
}
