package tallyvane.identity.web.registration

import tallyvane.platform.http.FieldError
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class RegisterProblems : Problems<RegisterFailure> {
    override fun Answers.of(failure: RegisterFailure): Problem = when (failure) {
        is RegisterFailure.EmailTaken -> conflicting("An account with this email already exists")
        is RegisterFailure.InvalidPassword -> invalid(listOf(FieldError("password", "password_length")))
    }
}
