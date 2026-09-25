package tallyvane.identity.domain.secondfactor

/**
 * Validated immutable policy version, snapshotted when an authentication attempt begins.
 */
public class AuthenticationPolicy(
    public val version: Long,
    rules: List<AuthenticationRule>,
    public val advancedAcknowledged: Boolean,
) {
    public val rules: Map<PrimaryMethod, AuthenticationRule> = rules.associateBy { it.primary }

    init {
        require(version > 0) { "Policy version must be positive" }
        require(rules.size == PrimaryMethod.entries.size && this.rules.size == rules.size) {
            "Policy must contain exactly one rule per primary method"
        }
        require(rules.any { it.enabled }) { "At least one primary sign-in method must be enabled" }
        require(
            rules.all { rule ->
                rule.primary != PrimaryMethod.EMAIL_CODE ||
                    SecondFactorKind.EMAIL_OTP !in rule.allowedMethods ||
                    rule.requirement != MfaRequirement.REQUIRED ||
                    advancedAcknowledged
            },
        ) { "Email MFA after email sign-in requires explicit advanced acknowledgement" }
    }

    public fun rule(method: PrimaryMethod): AuthenticationRule = rules.getValue(method)

    public companion object {
        public fun defaults(version: Long = 1): AuthenticationPolicy = AuthenticationPolicy(
            version,
            listOf(
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
            ),
            false,
        )
    }
}
