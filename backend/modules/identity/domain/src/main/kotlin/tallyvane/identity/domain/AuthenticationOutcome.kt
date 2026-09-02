package tallyvane.identity.domain

/**
 * What happened when a primary credential was checked — closed on purpose (Special Case, Fowler),
 * so a caller cannot forget a branch the way a nullable return or an exception for an expected
 * outcome would let it.
 *
 * `RequiresSecondFactor` is not a case yet: nothing in this pass enables a second factor, and a
 * case nothing can construct would be a promise this pass cannot keep. It arrives with the second
 * factor slice.
 */
public sealed interface AuthenticationOutcome {
    public data class Success(public val userId: UserId) : AuthenticationOutcome

    /**
     * Never distinguishes "no such account" from "wrong password" — see `SignInWithPasswordUseCase`.
     */
    public data object InvalidCredential : AuthenticationOutcome

    public data object AccountDisabled : AuthenticationOutcome

    public data object RateLimited : AuthenticationOutcome
}
