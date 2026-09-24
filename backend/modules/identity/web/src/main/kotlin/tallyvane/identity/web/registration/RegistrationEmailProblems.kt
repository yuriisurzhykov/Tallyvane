package tallyvane.identity.web.registration

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class RegistrationEmailProblems : Problems<RegistrationEmailFailure> {
    override fun Answers.of(failure: RegistrationEmailFailure): Problem = when (failure) {
        is RegistrationEmailFailure.InvalidCode -> unauthorized("The registration code was not accepted")
    }
}
