package tallyvane.identity.domain.secondfactor

public data class AuthenticationRule(
    public val primary: PrimaryMethod,
    public val enabled: Boolean,
    public val requirement: MfaRequirement,
    public val allowedMethods: Set<SecondFactorKind>,
) {
    public fun available(
        enrolled: Set<SecondFactorKind>,
        advancedAcknowledged: Boolean = false,
    ): Set<SecondFactorKind> = allowedMethods.intersect(enrolled).filterTo(mutableSetOf()) { method ->
        advancedAcknowledged || primary != PrimaryMethod.EMAIL_CODE || method != SecondFactorKind.EMAIL_OTP
    }

    public fun requiresEnrollment(enrolled: Set<SecondFactorKind>, advancedAcknowledged: Boolean = false): Boolean =
        enabled && requirement == MfaRequirement.REQUIRED && available(enrolled, advancedAcknowledged).isEmpty()
}
