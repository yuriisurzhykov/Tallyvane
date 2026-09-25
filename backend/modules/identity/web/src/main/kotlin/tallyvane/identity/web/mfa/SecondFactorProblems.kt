package tallyvane.identity.web.mfa

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class SecondFactorProblems : Problems<SecondFactorFailure> {
    override fun Answers.of(failure: SecondFactorFailure): Problem = when (failure) {
        is SecondFactorFailure.WrongCode -> unauthorized("The presented code was not accepted")
        is SecondFactorFailure.Expired -> missing("This pending authentication has expired; sign in again")
        is SecondFactorFailure.UnknownPending -> missing("No pending authentication with this id")
        is SecondFactorFailure.RateLimited -> tooManyRequests("Too many attempts; try again later")
        is SecondFactorFailure.UnsupportedMethod -> missing("No second-factor method registered for this kind")
    }
}
