package tallyvane.identity.domain.outcome

import tallyvane.identity.domain.user.UserId

/**
 * What happened when a new account was requested.
 *
 * A separate closed set from [AuthenticationOutcome], not a shared "outcome" type widened to
 * cover both: registering and signing in fail in genuinely different ways — there is no
 * `InvalidCredential` or `RateLimited` here — and one type trying to mean both would carry cases
 * that make no sense for whichever action did not produce them.
 */
public sealed interface RegisterOutcome {
    public data class Registered(public val userId: UserId) : RegisterOutcome

    public data object EmailTaken : RegisterOutcome
}
