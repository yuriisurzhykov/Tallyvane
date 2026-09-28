package tallyvane.identity.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class RegisterProblems : Problems<RegisterFailure> {
    override fun Answers.of(failure: RegisterFailure): Problem = when (failure) {
        RegisterFailure.EmailTaken -> conflicting("An account with this email already exists")
    }
}
