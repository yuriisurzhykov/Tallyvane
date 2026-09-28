package tallyvane.identity.application.secondfactor

import tallyvane.identity.application.IssuedSession
import tallyvane.identity.domain.outcome.SecondFactorOutcome

/**
 * What happened when [VerifySecondFactorUseCase] ran to completion — either a session was issued,
 * or it wasn't, for whichever reason [SecondFactorOutcome] already names. The same two-case shape
 * [tallyvane.identity.application.SignInOutcome] already uses for a primary credential, kept as a
 * separate type because [SecondFactorOutcome] and
 * [tallyvane.identity.domain.outcome.AuthenticationOutcome] name different failure reasons.
 */
public sealed interface VerifySecondFactorOutcome {
    public data class Issued(public val session: IssuedSession) : VerifySecondFactorOutcome

    public data class NotCompleted(public val reason: SecondFactorOutcome) : VerifySecondFactorOutcome
}
