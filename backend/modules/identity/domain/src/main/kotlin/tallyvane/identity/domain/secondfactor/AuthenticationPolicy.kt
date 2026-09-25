package tallyvane.identity.domain.secondfactor

/** Versioned schemes the authentication evaluator may use for sign-in and account actions. */
public class AuthenticationPolicy private constructor(
    public val version: Long,
    schemes: List<AuthenticationScheme>,
    public val advancedAcknowledged: Boolean,
    legacyRules: List<AuthenticationRule>?,
) {
    public val schemes: List<AuthenticationScheme> = schemes.toList()

    /** Compatibility view for existing callers while they migrate to [schemes]. */
    public val rules: Map<PrimaryMethod, AuthenticationRule> = legacyRules?.associateBy { it.primary }
        ?: PrimaryMethod.entries.associateWith(::legacyRule)

    init {
        require(version > 0) { "Policy version must be positive" }
        require(this.schemes.isNotEmpty()) { "Policy must contain at least one scheme" }
        require(this.schemes.map { it.id }.toSet().size == this.schemes.size) { "Scheme ids must be unique" }
        require(this.schemes.any { it.enabled && it.action == AuthenticationAction.SIGN_IN }) {
            "At least one sign-in scheme must be enabled"
        }
        val enabledPrimaryTokens = this.schemes.asSequence()
            .filter { it.enabled && it.action == AuthenticationAction.SIGN_IN }
            .flatMap { it.requiredTokens.asSequence() }
            .filter(AuthenticationTokenKind::isPrimary)
            .toSet()
        require(enabledPrimaryTokens.all { primary ->
            this.schemes.any { scheme ->
                scheme.enabled && scheme.action == AuthenticationAction.SIGN_IN &&
                    scheme.requiredTokens == setOf(primary)
            }
        }) { "Every enabled primary sign-in token must have a primary-only scheme" }
        require(
            this.schemes.all { scheme ->
                AuthenticationTokenKind.EMAIL_SIGN_IN_CODE !in scheme.requiredTokens ||
                    AuthenticationTokenKind.EMAIL_FACTOR_CODE !in scheme.requiredTokens ||
                    advancedAcknowledged
            },
        ) { "Combining email sign-in and email MFA requires explicit advanced acknowledgement" }
    }

    public fun schemesFor(action: AuthenticationAction): List<AuthenticationScheme> =
        schemes.filter { it.enabled && it.action == action }

    /** Return every satisfiable scheme at the highest configured rank for this proof context. */
    public fun strongest(
        action: AuthenticationAction,
        availableTokens: Set<AuthenticationTokenKind>,
        presentedPrimary: AuthenticationTokenKind? = null,
    ): List<AuthenticationScheme> {
        val candidates = schemesFor(action).filter { scheme ->
            (presentedPrimary == null || presentedPrimary in scheme.requiredTokens) &&
                scheme.isSatisfiedBy(availableTokens)
        }
        val maximumRank = candidates.maxOfOrNull { it.assuranceRank } ?: return emptyList()
        return candidates.filter { it.assuranceRank == maximumRank }
    }

    private fun legacyRule(primary: PrimaryMethod): AuthenticationRule {
        val primaryToken = primary.toAuthenticationToken()
        val signIn = schemesFor(AuthenticationAction.SIGN_IN).filter { primaryToken in it.requiredTokens }
        val allowedFactors = signIn.flatMapTo(mutableSetOf()) { scheme ->
            scheme.requiredTokens.filter(AuthenticationTokenKind::isSecondFactor).map { it.toSecondFactorKind() }
        }
        val hasCombinedScheme = signIn.any { scheme -> scheme.requiredTokens.any(AuthenticationTokenKind::isSecondFactor) }
        return AuthenticationRule(
            primary = primary,
            enabled = signIn.isNotEmpty(),
            requirement = if (hasCombinedScheme) MfaRequirement.IF_ENROLLED else MfaRequirement.DISABLED,
            allowedMethods = allowedFactors,
        )
    }

    public companion object {
        /**
         * Compatibility constructor for policy rules saved by the first release. New code should
         * use [fromSchemes]. Legacy REQUIRED enrollment intentionally becomes optional enrollment:
         * absence of a second factor never blocks a successful primary sign-in.
         */
        public operator fun invoke(
            version: Long,
            rules: List<AuthenticationRule>,
            advancedAcknowledged: Boolean,
        ): AuthenticationPolicy {
            require(rules.size == PrimaryMethod.entries.size && rules.map { it.primary }.toSet().size == rules.size) {
                "Legacy policy must contain exactly one rule per primary method"
            }
            return AuthenticationPolicy(
                version,
                rules.flatMap { it.toSchemes(advancedAcknowledged) },
                advancedAcknowledged,
                rules,
            )
        }

        public fun fromSchemes(
            version: Long,
            schemes: List<AuthenticationScheme>,
            advancedAcknowledged: Boolean = false,
        ): AuthenticationPolicy = AuthenticationPolicy(version, schemes, advancedAcknowledged, null)

        public fun defaults(version: Long = 1): AuthenticationPolicy {
            val legacyRules = listOf(
                AuthenticationRule(
                    PrimaryMethod.PASSWORD,
                    true,
                    MfaRequirement.IF_ENROLLED,
                    setOf(SecondFactorKind.TOTP, SecondFactorKind.EMAIL_OTP, SecondFactorKind.BACKUP_CODE),
                ),
                AuthenticationRule(
                    PrimaryMethod.GOOGLE,
                    true,
                    MfaRequirement.IF_ENROLLED,
                    setOf(SecondFactorKind.TOTP, SecondFactorKind.BACKUP_CODE),
                ),
                AuthenticationRule(
                    PrimaryMethod.EMAIL_CODE,
                    true,
                    MfaRequirement.IF_ENROLLED,
                    setOf(SecondFactorKind.TOTP, SecondFactorKind.BACKUP_CODE),
                ),
            )
            return fromSchemes(
                version,
                legacyRules.flatMap { it.toSchemes(advancedAcknowledged = false) },
            )
        }

        private fun AuthenticationRule.toSchemes(advancedAcknowledged: Boolean): List<AuthenticationScheme> {
            if (!enabled) return emptyList()
            val primaryToken = primary.toAuthenticationToken()
            val result = mutableListOf(
                AuthenticationScheme(
                    id = "signin-${primary.name.lowercase()}-primary",
                    action = AuthenticationAction.SIGN_IN,
                    requiredTokens = setOf(primaryToken),
                    assuranceRank = 1,
                ),
            )
            val factors = if (requirement == MfaRequirement.DISABLED) emptySet() else allowedMethods
            factors.forEach { factor ->
                val token = factor.toAuthenticationToken()
                if (primaryToken == AuthenticationTokenKind.EMAIL_SIGN_IN_CODE &&
                    token == AuthenticationTokenKind.EMAIL_FACTOR_CODE && !advancedAcknowledged
                ) {
                    return@forEach
                }
                result += AuthenticationScheme(
                    id = "signin-${primary.name.lowercase()}-${factor.name.lowercase()}",
                    action = AuthenticationAction.SIGN_IN,
                    requiredTokens = setOf(primaryToken, token),
                    assuranceRank = 2,
                )
            }
            result += AuthenticationScheme(
                id = "change-primary-${primary.name.lowercase()}-primary",
                action = AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
                requiredTokens = setOf(primaryToken),
                assuranceRank = 1,
            )
            result += AuthenticationScheme(
                id = "manage-factors-${primary.name.lowercase()}-primary",
                action = AuthenticationAction.MANAGE_SECOND_FACTORS,
                requiredTokens = setOf(primaryToken),
                assuranceRank = 1,
            )
            SecondFactorKind.entries.forEach { factor ->
                val token = factor.toAuthenticationToken()
                val pairingAllowed = primaryToken != AuthenticationTokenKind.EMAIL_SIGN_IN_CODE ||
                    token != AuthenticationTokenKind.EMAIL_FACTOR_CODE || advancedAcknowledged
                if (pairingAllowed) {
                    for (action in listOf(
                        AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
                        AuthenticationAction.MANAGE_SECOND_FACTORS,
                    )) {
                        val actionSlug = if (action == AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL) "change-primary" else "manage-factors"
                        result += AuthenticationScheme(
                            id = "$actionSlug-${primary.name.lowercase()}-${factor.name.lowercase()}",
                            action = action,
                            requiredTokens = setOf(primaryToken, token),
                            assuranceRank = 2,
                        )
                    }
                }
                result += AuthenticationScheme(
                    id = "change-primary-${factor.name.lowercase()}-factor",
                    action = AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
                    requiredTokens = setOf(token),
                    assuranceRank = 2,
                )
                result += AuthenticationScheme(
                    id = "manage-factors-${factor.name.lowercase()}-factor",
                    action = AuthenticationAction.MANAGE_SECOND_FACTORS,
                    requiredTokens = setOf(token),
                    assuranceRank = 2,
                )
            }
            return result
        }

        private fun PrimaryMethod.toAuthenticationToken(): AuthenticationTokenKind = when (this) {
            PrimaryMethod.PASSWORD -> AuthenticationTokenKind.PASSWORD
            PrimaryMethod.GOOGLE -> AuthenticationTokenKind.GOOGLE
            PrimaryMethod.EMAIL_CODE -> AuthenticationTokenKind.EMAIL_SIGN_IN_CODE
        }

        private fun SecondFactorKind.toAuthenticationToken(): AuthenticationTokenKind = when (this) {
            SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
            SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
            SecondFactorKind.BACKUP_CODE -> AuthenticationTokenKind.BACKUP_CODE
        }
    }
}

private fun PrimaryMethod.toAuthenticationToken(): AuthenticationTokenKind = when (this) {
    PrimaryMethod.PASSWORD -> AuthenticationTokenKind.PASSWORD
    PrimaryMethod.GOOGLE -> AuthenticationTokenKind.GOOGLE
    PrimaryMethod.EMAIL_CODE -> AuthenticationTokenKind.EMAIL_SIGN_IN_CODE
}

private fun SecondFactorKind.toAuthenticationToken(): AuthenticationTokenKind = when (this) {
    SecondFactorKind.TOTP -> AuthenticationTokenKind.TOTP
    SecondFactorKind.EMAIL_OTP -> AuthenticationTokenKind.EMAIL_FACTOR_CODE
    SecondFactorKind.BACKUP_CODE -> AuthenticationTokenKind.BACKUP_CODE
}

private fun AuthenticationTokenKind.toSecondFactorKind(): SecondFactorKind = when (this) {
    AuthenticationTokenKind.TOTP -> SecondFactorKind.TOTP
    AuthenticationTokenKind.EMAIL_FACTOR_CODE -> SecondFactorKind.EMAIL_OTP
    AuthenticationTokenKind.BACKUP_CODE -> SecondFactorKind.BACKUP_CODE
    else -> error("$this is not a second-factor token")
}
