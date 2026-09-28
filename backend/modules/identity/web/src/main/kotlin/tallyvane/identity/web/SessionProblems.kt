package tallyvane.identity.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class SessionProblems : Problems<SessionFailure> {
    override fun Answers.of(failure: SessionFailure): Problem = when (failure) {
        SessionFailure.SessionNotFound -> missing("No session with this id")
        SessionFailure.RefreshInvalid -> unauthorized("This refresh token is no longer valid; sign in again")
        SessionFailure.RefreshReused -> unauthorized("This session was signed out because a refresh token was reused")
        SessionFailure.NotAuthenticated -> unauthorized("Sign in to access this resource")
    }
}
