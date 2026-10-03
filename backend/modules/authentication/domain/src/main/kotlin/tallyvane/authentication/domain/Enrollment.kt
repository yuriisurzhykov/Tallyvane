package tallyvane.authentication.domain

/**
 * The factors one account has set up and confirmed.
 *
 * A factor added but not yet confirmed with its first code is not here: it protects nothing and
 * must not count (ADR-078). Before the account is known, at the start of a sign-in, the enrollment
 * is [Unknown] and only steps every account can pass apply.
 */
public data class Enrollment(private val kinds: Set<FactorKind>) {
    /**
     * Whether [kind] is set up for this account.
     */
    public fun includes(kind: FactorKind): Boolean = kind in kinds

    public companion object {
        /**
         * The enrollment of an account nobody has identified yet.
         */
        public val Unknown: Enrollment = Enrollment(emptySet())

        /**
         * What an account has set up, given its TOTP and its recovery codes, either of which it may not
         * have.
         *
         * TOTP counts while it is active. A recovery code counts while at least one is unspent, so a
         * person whose seed was retired still has a second step until the codes run out (ADR-093).
         */
        public fun of(totp: TotpEnrollment?, codes: RecoveryCodes?): Enrollment = Enrollment(
            setOfNotNull(
                FactorKind.Totp.takeIf { totp?.isActive() == true },
                FactorKind.RecoveryCode.takeIf { (codes?.remaining() ?: 0) > 0 },
            ),
        )
    }
}
