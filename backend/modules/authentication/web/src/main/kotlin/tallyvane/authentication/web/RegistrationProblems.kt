package tallyvane.authentication.web

import tallyvane.authentication.application.RegisterOutcome
import tallyvane.platform.http.FieldError
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way finishing the welcome form can fail means over HTTP.
 */
internal class RegistrationProblems : Problems<RegisterOutcome.Failed> {
    override fun Answers.of(failure: RegisterOutcome.Failed): Problem = when (failure) {
        is RegisterOutcome.Failed.NoRegistration -> missing("There is no registration to finish.")
        is RegisterOutcome.Failed.ConsentMissing -> invalid(listOf(FieldError("agreed", "consent-required")))
        is RegisterOutcome.Failed.NameRefused -> invalid(listOf(FieldError("name", "name-invalid")))
    }

    override fun toString(): String = "RegistrationProblems"
}
