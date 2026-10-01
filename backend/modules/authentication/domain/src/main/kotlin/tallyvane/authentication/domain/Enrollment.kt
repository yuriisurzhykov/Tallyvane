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
    }
}
