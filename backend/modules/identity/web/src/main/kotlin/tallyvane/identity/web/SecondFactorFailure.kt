package tallyvane.identity.web

import tallyvane.platform.kernel.Failure

/**
 * Every reason enrolling, confirming or verifying a second factor did not succeed, grouped under
 * one root so [SecondFactorProblems] maps all three MFA routes with one table.
 *
 * [UnsupportedMethod] is the enroll/confirm use cases' own `null`/`false` for a
 * [tallyvane.identity.domain.secondfactor.SecondFactorKind] the registry has nothing registered
 * for — a deployment misconfiguration a real client should never trigger, per those use cases'
 * own KDoc, but still a 4xx from the caller's `kind` field, not a 500.
 */
internal sealed interface SecondFactorFailure : Failure {
    data object WrongCode : SecondFactorFailure

    data object Expired : SecondFactorFailure

    data object UnknownPending : SecondFactorFailure

    data object RateLimited : SecondFactorFailure

    data object UnsupportedMethod : SecondFactorFailure
}
