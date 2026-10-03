package tallyvane.platform.http

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each refusal of the edge means in HTTP (ADR-088), within the set of meanings of ADR-062.
 */
internal class AccessProblems : Problems<AccessFailure> {
    override fun Answers.of(failure: AccessFailure): Problem = when (failure) {
        AccessFailure.SignInRequired -> signInRequired("Sign in to use this.")

        AccessFailure.SessionExpired -> sessionExpired("Your session has ended. Sign in again.")

        AccessFailure.StepUpRequired -> stepUpRequired("Confirm that it is you to do this.")

        AccessFailure.ForeignOrigin -> forbidden(
            "Requests that change anything are accepted only from the application's own pages.",
        )
    }
}
