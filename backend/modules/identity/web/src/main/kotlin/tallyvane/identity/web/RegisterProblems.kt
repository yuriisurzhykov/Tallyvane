package tallyvane.identity.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.platform.http.FieldError

internal class RegisterProblems : Problems<RegisterFailure> {
    override fun Answers.of(failure: RegisterFailure): Problem = when (failure) {
        RegisterFailure.EmailTaken -> conflicting("An account with this email already exists")
        RegisterFailure.InvalidPassword -> invalid(listOf(FieldError("password", "password_length")))
    }
}
