package tallyvane.identity.domain.secondfactor

/**
 * Versioned schemes the authentication evaluator may use for sign-in and account actions.
 */
public class AuthenticationPolicy internal constructor(
    public val version: Long,
    schemes: List<AuthenticationScheme>,
    public val advancedAcknowledged: Boolean,
    legacyRules: List<AuthenticationRule>?,
) {
    public val schemes: List<AuthenticationScheme> = schemes.toList()

    /**
     * Compatibility view for existing callers while they migrate to [schemes].*/
    public val rules: Map<PrimaryMethod, AuthenticationRule> = legacyRules?.associateBy { it.primary }
        ?: AuthenticationPolicyCompatibility().legacyRules(this.schemes)

    init {
        require(version > 0) { "Policy version must be positive" }
        require(this.schemes.isNotEmpty()) { "Policy must contain at least one scheme" }
        require(this.schemes.map { it.id }.toSet().size == this.schemes.size) { "Scheme ids must be unique" }
        require(this.schemes.any { it.enabled && it.action == AuthenticationAction.SIGN_IN }) {
            "At least one sign-in scheme must be enabled"
        }
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

    /**
     * Return every satisfiable scheme at the highest configured rank for this proof context.
     */
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
        ): AuthenticationPolicy = AuthenticationPolicySchemeFactory().fromLegacyRules(
            version,
            rules,
            advancedAcknowledged,
        )

        public fun fromSchemes(
            version: Long,
            schemes: List<AuthenticationScheme>,
            advancedAcknowledged: Boolean = false,
        ): AuthenticationPolicy = AuthenticationPolicy(version, schemes, advancedAcknowledged, null)

        public fun defaults(version: Long = 1): AuthenticationPolicy =
            AuthenticationPolicySchemeFactory().defaults(version)
    }
}
