package tallyvane.identity.domain.outcome

import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.PrimaryMethod
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.domain.user.UserId

/**
 * What happened when a primary credential was checked — closed on purpose (Special Case, Fowler),
 * so a caller cannot forget a branch the way a nullable return or an exception for an expected
 * outcome would let it.
 */
public sealed interface AuthenticationOutcome {
    public data class Success(public val userId: UserId) : AuthenticationOutcome

    /**
     * The credential checked out, but [userId] has at least one second factor enrolled, so no
     * session was issued yet — [pendingId] is where `POST /auth/mfa/verify` completes it, and
     * [availableMethods] is what the client may present.
     */
    public data class RequiresSecondFactor(
        public val pendingId: PendingAuthenticationId,
        public val availableMethods: Set<SecondFactorKind>,
    ) : AuthenticationOutcome

    /**
     * The primary credential checked out, but this policy requires enrollment before any session.
     */
    public data class RequiresEnrollment(
        public val pendingId: PendingAuthenticationId,
        public val primaryMethod: PrimaryMethod,
        public val requiredMethods: Set<SecondFactorKind>,
    ) : AuthenticationOutcome

    /**
     * Never distinguishes "no such account" from "wrong password" — see `SignInWithPasswordUseCase`.
     */
    public data object InvalidCredential : AuthenticationOutcome

    public data object AccountDisabled : AuthenticationOutcome

    public data object RateLimited : AuthenticationOutcome
}
