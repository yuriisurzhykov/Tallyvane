package tallyvane.identity.web

import tallyvane.identity.application.WhoAmIOutcome
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way asking who someone is can fail means over HTTP.
 */
internal class MeProblems : Problems<WhoAmIOutcome.Failed> {
    /**
     * The session names an account that is gone, so the session is no good any more, and the person is
     * told what a session that ended is told.
     */
    override fun Answers.of(failure: WhoAmIOutcome.Failed): Problem = when (failure) {
        is WhoAmIOutcome.Failed.Gone -> sessionExpired("This account no longer exists. Sign in again.")
    }

    override fun toString(): String = "MeProblems"
}
