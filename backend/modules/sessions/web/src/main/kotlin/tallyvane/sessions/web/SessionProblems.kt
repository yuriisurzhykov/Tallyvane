package tallyvane.sessions.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.sessions.application.Opened

/**
 * What each way asking for a session can fail means over HTTP.
 */
internal class SessionProblems : Problems<Opened.Failed> {
    override fun Answers.of(failure: Opened.Failed): Problem = when (failure) {
        is Opened.Failed.NothingToOpen -> missing("There is no completed sign-in to start a session from.")
    }

    override fun toString(): String = "SessionProblems"
}
