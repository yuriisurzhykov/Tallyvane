package tallyvane.identity.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class AuthenticationProblems : Problems<AuthenticationFailure> {
    /**
     * One shared message for [AuthenticationFailure.InvalidCredential] across password and both
     * Google methods — a per-method wording ("incorrect password" vs "invalid Google token")
     * would tell an attacker which reason applied, which the outcome itself already refuses to
     * distinguish (`AuthenticationOutcome.InvalidCredential`'s own KDoc).
     */
    override fun Answers.of(failure: AuthenticationFailure): Problem = when (failure) {
        AuthenticationFailure.InvalidCredential -> unauthorized("The presented credential was not accepted")
        AuthenticationFailure.AccountDisabled -> forbidden("This account has been disabled")
        AuthenticationFailure.RateLimited -> tooManyRequests("Too many attempts; try again later")
    }
}
