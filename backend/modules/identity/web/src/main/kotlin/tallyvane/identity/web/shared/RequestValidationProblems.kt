package tallyvane.identity.web.shared

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class RequestValidationProblems : Problems<RequestValidationFailure> {
    override fun Answers.of(failure: RequestValidationFailure): Problem = when (failure) {
        is RequestValidationFailure.FieldsInvalid -> invalid(failure.errors)
    }
}
