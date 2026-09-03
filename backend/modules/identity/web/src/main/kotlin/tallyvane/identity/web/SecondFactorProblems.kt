package tallyvane.identity.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class SecondFactorProblems : Problems<SecondFactorFailure> {
    override fun Answers.of(failure: SecondFactorFailure): Problem = when (failure) {
        SecondFactorFailure.WrongCode -> unauthorized("The presented code was not accepted")
        SecondFactorFailure.Expired -> missing("This pending authentication has expired; sign in again")
        SecondFactorFailure.UnknownPending -> missing("No pending authentication with this id")
        SecondFactorFailure.RateLimited -> tooManyRequests("Too many attempts; try again later")
        SecondFactorFailure.UnsupportedMethod -> missing("No second-factor method registered for this kind")
    }
}
