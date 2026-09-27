package tallyvane.identity.domain.secondfactor

/**
 * Builds compatibility policies used to bootstrap databases and older policy callers.
 */
internal class AuthenticationPolicySchemeFactory {
    private val compatibility = AuthenticationPolicyCompatibility()

    fun fromLegacyRules(
        version: Long,
        rules: List<AuthenticationRule>,
        advancedAcknowledged: Boolean,
    ): AuthenticationPolicy {
        require(rules.size == PrimaryMethod.entries.size && rules.map { it.primary }.toSet().size == rules.size) {
            "Legacy policy must contain exactly one rule per primary method"
        }
        val schemes = rules.flatMap { schemesForRule(it, advancedAcknowledged) } + factorOnlyActionSchemes()
        return AuthenticationPolicy(version, schemes, advancedAcknowledged, rules)
    }

    fun defaults(version: Long): AuthenticationPolicy = AuthenticationPolicy(
        version,
        defaultRules().flatMap { schemesForRule(it, advancedAcknowledged = false) } + factorOnlyActionSchemes(),
        false,
        null,
    )

    private fun defaultRules(): List<AuthenticationRule> = listOf(
        AuthenticationRule(
            PrimaryMethod.PASSWORD,
            true,
            MfaRequirement.IF_ENROLLED,
            setOf(SecondFactorKind.TOTP, SecondFactorKind.EMAIL_OTP),
        ),
        AuthenticationRule(
            PrimaryMethod.GOOGLE,
            true,
            MfaRequirement.IF_ENROLLED,
            setOf(SecondFactorKind.TOTP),
        ),
        AuthenticationRule(
            PrimaryMethod.EMAIL_CODE,
            true,
            MfaRequirement.IF_ENROLLED,
            setOf(SecondFactorKind.TOTP),
        ),
    )

    private fun schemesForRule(rule: AuthenticationRule, advancedAcknowledged: Boolean): List<AuthenticationScheme> {
        if (!rule.enabled) return emptyList()
        require(
            advancedAcknowledged ||
                rule.requirement == MfaRequirement.DISABLED ||
                rule.primary != PrimaryMethod.EMAIL_CODE ||
                SecondFactorKind.EMAIL_OTP !in rule.allowedMethods,
        ) { "Combining email sign-in and email MFA requires explicit advanced acknowledgement" }
        val primaryToken = compatibility.primaryToken(rule.primary)
        return signInSchemes(rule, primaryToken, advancedAcknowledged) + actionSchemes(
            rule.primary,
            primaryToken,
            advancedAcknowledged,
        )
    }

    private fun signInSchemes(
        rule: AuthenticationRule,
        primaryToken: AuthenticationTokenKind,
        advancedAcknowledged: Boolean,
    ): List<AuthenticationScheme> {
        val primaryOnly = AuthenticationScheme(
            id = "signin-${rule.primary.name.lowercase()}-primary",
            action = AuthenticationAction.SIGN_IN,
            requiredTokens = setOf(primaryToken),
            assuranceRank = 1,
        )
        val factors = if (rule.requirement == MfaRequirement.DISABLED) emptySet() else rule.allowedMethods
        return listOf(primaryOnly) + factors
            .filter { factor ->
                isPairingAllowed(primaryToken, compatibility.factorToken(factor), advancedAcknowledged)
            }
            .map { factor ->
                AuthenticationScheme(
                    id = "signin-${rule.primary.name.lowercase()}-${factor.name.lowercase()}",
                    action = AuthenticationAction.SIGN_IN,
                    requiredTokens = setOf(primaryToken, compatibility.factorToken(factor)),
                    assuranceRank = 2,
                )
            }
    }

    private fun actionSchemes(
        primary: PrimaryMethod,
        primaryToken: AuthenticationTokenKind,
        advancedAcknowledged: Boolean,
    ): List<AuthenticationScheme> = primaryOnlyActionSchemes(primary, primaryToken) +
        SecondFactorKind.entries
            .filter { factor ->
                isPairingAllowed(primaryToken, compatibility.factorToken(factor), advancedAcknowledged)
            }
            .flatMap { factor -> factorActionSchemes(primary.name.lowercase(), primaryToken, factor) }

    private fun primaryOnlyActionSchemes(
        primary: PrimaryMethod,
        token: AuthenticationTokenKind,
    ): List<AuthenticationScheme> = protectedActions.map { action ->
        AuthenticationScheme(
            id = "${action.slug}-${primary.name.lowercase()}-primary",
            action = action,
            requiredTokens = setOf(token),
            assuranceRank = 1,
        )
    }

    private fun factorActionSchemes(
        primary: String,
        primaryToken: AuthenticationTokenKind,
        factor: SecondFactorKind,
    ): List<AuthenticationScheme> = protectedActions.map { action ->
        AuthenticationScheme(
            id = "${action.slug}-$primary-${factor.name.lowercase()}",
            action = action,
            requiredTokens = setOf(primaryToken, compatibility.factorToken(factor)),
            assuranceRank = 2,
        )
    }

    private fun factorOnlyActionSchemes(): List<AuthenticationScheme> = SecondFactorKind.entries.flatMap { factor ->
        protectedActions.map { action ->
            AuthenticationScheme(
                id = "${action.slug}-${factor.name.lowercase()}-factor",
                action = action,
                requiredTokens = setOf(compatibility.factorToken(factor)),
                assuranceRank = 2,
            )
        }
    }

    private fun isPairingAllowed(
        primary: AuthenticationTokenKind,
        factor: AuthenticationTokenKind,
        advancedAcknowledged: Boolean,
    ): Boolean = primary != AuthenticationTokenKind.EMAIL_SIGN_IN_CODE ||
        factor != AuthenticationTokenKind.EMAIL_FACTOR_CODE ||
        advancedAcknowledged

    private val protectedActions = listOf(
        AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
        AuthenticationAction.MANAGE_SECOND_FACTORS,
    )

    private val AuthenticationAction.slug: String
        get() = when (this) {
            AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL -> "change-primary"
            AuthenticationAction.MANAGE_SECOND_FACTORS -> "manage-factors"
            AuthenticationAction.SIGN_IN -> error("Sign-in does not have an action-proof scheme")
        }
}
