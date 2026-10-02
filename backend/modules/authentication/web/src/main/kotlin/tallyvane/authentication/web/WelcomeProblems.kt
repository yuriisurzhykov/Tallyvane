package tallyvane.authentication.web

import tallyvane.authentication.application.ShowRegistrationOutcome
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way the welcome form can be refused means over HTTP.
 */
internal class WelcomeProblems : Problems<ShowRegistrationOutcome.Failed> {
    override fun Answers.of(failure: ShowRegistrationOutcome.Failed): Problem = when (failure) {
        is ShowRegistrationOutcome.Failed.NoRegistration -> missing("There is no registration to finish.")
    }

    override fun toString(): String = "WelcomeProblems"
}
