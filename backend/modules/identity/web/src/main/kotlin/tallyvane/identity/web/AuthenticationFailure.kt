package tallyvane.identity.web

import tallyvane.platform.kernel.Failure

/**
 * Every reason a primary sign-in — password, either Google method — did not issue a session,
 * grouped under one root so [AuthenticationProblems] maps all three sign-in routes with one table.
 * [tallyvane.identity.domain.outcome.AuthenticationOutcome.RequiresSecondFactor] and `.Success` are
 * deliberately absent: neither is a failure a route answers with [tallyvane.platform.http.Refused]
 * — the first is a 200 carrying where to complete the second factor, the second never reaches the
 * web layer as a `SignInOutcome.NotIssued` reason at all.
 */
internal sealed interface AuthenticationFailure : Failure {
    data object InvalidCredential : AuthenticationFailure

    data object AccountDisabled : AuthenticationFailure

    data object RateLimited : AuthenticationFailure
}
