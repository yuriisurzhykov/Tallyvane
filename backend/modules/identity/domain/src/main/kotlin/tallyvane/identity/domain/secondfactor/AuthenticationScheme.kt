package tallyvane.identity.domain.secondfactor

/**
 * One AND-combination of proofs. Schemes with the same [action] are alternatives; the evaluator
 * selects only among the highest-assurance schemes satisfiable by the user's configured tokens.
 */
public data class AuthenticationScheme(
    public val id: String,
    public val action: AuthenticationAction,
    public val requiredTokens: Set<AuthenticationTokenKind>,
    public val assuranceRank: Int,
    public val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank() && id.length <= 80) { "Scheme id must contain 1 to 80 characters" }
        require(requiredTokens.isNotEmpty()) { "A scheme must require at least one token" }
        require(assuranceRank > 0) { "Scheme assurance rank must be positive" }
        if (action == AuthenticationAction.SIGN_IN) {
            require(requiredTokens.count(AuthenticationTokenKind::isPrimary) == 1) {
                "A sign-in scheme must contain exactly one primary token"
            }
            require(requiredTokens.count(AuthenticationTokenKind::isSecondFactor) <= 1) {
                "A sign-in scheme may require at most one second-factor token"
            }
        }
    }

    public fun isSatisfiedBy(available: Set<AuthenticationTokenKind>): Boolean =
        enabled && requiredTokens.all(available::contains)
}
