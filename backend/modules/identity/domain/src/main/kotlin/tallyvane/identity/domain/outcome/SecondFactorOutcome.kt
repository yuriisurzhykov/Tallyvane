package tallyvane.identity.domain.outcome

import tallyvane.identity.domain.user.UserId

/**
 * What happened when a second-factor code was checked against a
 * [tallyvane.identity.domain.secondfactor.PendingAuthentication] — the [SecondFactorOutcome] arm of
 * the same closed-outcome shape [AuthenticationOutcome] already uses for a primary credential, so a
 * caller cannot forget a branch.
 *
 * Carries only [UserId] on success, the same reason [AuthenticationOutcome.Success] does: building
 * a [tallyvane.identity.contract.Principal] and issuing a session is
 * [tallyvane.identity.application.SessionIssuer]'s job, one layer up, not this type's to know about
 * (`domain` may not see `application`'s `IssuedSession`, per `modules.yaml`).
 */
public sealed interface SecondFactorOutcome {
    public data class Completed(public val userId: UserId) : SecondFactorOutcome

    /**
     * The presented code does not match — never distinguishes "wrong code" from "right code, wrong
     * factor kind", the same "don't leak why a credential was refused" choice
     * [AuthenticationOutcome.InvalidCredential] already makes for a password.
     */
    public data object WrongCode : SecondFactorOutcome

    /**
     * The [tallyvane.identity.domain.secondfactor.PendingAuthentication] this code was checked
     * against has outlived its own `expiresAt` — a distinct case from [WrongCode] because the
     * client's own next step differs: restart primary sign-in, not retry the code.
     */
    public data object Expired : SecondFactorOutcome

    /**
     * No [tallyvane.identity.domain.secondfactor.PendingAuthentication] exists for the presented
     * `pendingId` — already completed, already expired and swept, or never issued.
     */
    public data object UnknownPending : SecondFactorOutcome

    public data object RateLimited : SecondFactorOutcome
}
