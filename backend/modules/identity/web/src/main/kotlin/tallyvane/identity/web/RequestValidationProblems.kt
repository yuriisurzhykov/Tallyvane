package tallyvane.identity.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.identity.web.auth.RequestValidationFailure

internal class RequestValidationProblems : Problems<RequestValidationFailure> {
    override fun Answers.of(failure: RequestValidationFailure): Problem = when (failure) {
        is RequestValidationFailure.FieldsInvalid -> invalid(failure.errors)
    }
}
