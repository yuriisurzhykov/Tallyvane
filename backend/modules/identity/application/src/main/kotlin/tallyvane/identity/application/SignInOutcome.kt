package tallyvane.identity.application

import tallyvane.identity.domain.outcome.AuthenticationOutcome

/**
 * What happened when a sign-in action ran to completion — either a session was issued, or it
 * wasn't, for whichever reason [AuthenticationOutcome] already names.
 *
 * ```
 * SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential) // wrong password, no session
 * ```
 */
public sealed interface SignInOutcome {
    public data class Issued(public val session: IssuedSession) : SignInOutcome

    public data class NotIssued(public val reason: AuthenticationOutcome) : SignInOutcome
}
