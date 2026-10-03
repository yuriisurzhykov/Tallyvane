package tallyvane.authentication.web

import tallyvane.authentication.application.Verification
import tallyvane.platform.http.FieldError
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way answering a second step can fail means over HTTP.
 *
 * A wrong answer and a pause are told apart, and both come with `Retry-After`, which the route sets from
 * the failure: the problem itself carries no number.
 */
internal class SecondFactorCodeProblems : Problems<Verification.Failed> {
    override fun Answers.of(failure: Verification.Failed): Problem = when (failure) {
        is Verification.Failed.WrongCode -> invalid(listOf(FieldError("code", "wrong-code")))
        is Verification.Failed.Paused -> slowDown("Wait before trying another code.")
        is Verification.Failed.Closed -> gone("This sign-in is over. Start again.")
        is Verification.Failed.NotWanted -> conflicting("This sign-in is not waiting for that answer.")
        is Verification.Failed.Busy -> conflicting("Another request changed this sign-in. Ask where it stands.")
    }

    override fun toString(): String = "SecondFactorCodeProblems"
}
