package tallyvane.identity.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class RequestValidationProblems : Problems<RequestValidationFailure> {
    override fun Answers.of(failure: RequestValidationFailure): Problem = invalid(failure.errors)
}
