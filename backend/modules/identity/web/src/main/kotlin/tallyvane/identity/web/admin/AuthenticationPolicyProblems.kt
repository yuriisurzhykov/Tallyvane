package tallyvane.identity.web.admin

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class AuthenticationPolicyProblems : Problems<AuthenticationPolicyFailure> {
    override fun Answers.of(failure: AuthenticationPolicyFailure): Problem = when (failure) {
        AuthenticationPolicyFailure.Forbidden -> forbidden("Administrator access is required")
        AuthenticationPolicyFailure.Conflict -> conflicting("The policy changed; reload it and try again")
        AuthenticationPolicyFailure.Invalid -> invalid(emptyList(), "The authentication policy is invalid")
    }
}
