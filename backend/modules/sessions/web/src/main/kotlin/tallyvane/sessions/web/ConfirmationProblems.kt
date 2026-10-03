package tallyvane.sessions.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.sessions.application.Confirmed

/**
 * What each way confirming a dangerous act can fail means over HTTP.
 */
internal class ConfirmationProblems : Problems<Confirmed.Failed> {
    override fun Answers.of(failure: Confirmed.Failed): Problem = when (failure) {
        is Confirmed.Failed.SignInRequired -> signInRequired()
        is Confirmed.Failed.SessionExpired -> sessionExpired()
        is Confirmed.Failed.NothingToConfirm -> conflicting("There is no finished confirmation to take.")
        is Confirmed.Failed.WrongAccount -> forbidden("The confirmation is not the signed-in person's own.")
    }

    override fun toString(): String = "ConfirmationProblems"
}
