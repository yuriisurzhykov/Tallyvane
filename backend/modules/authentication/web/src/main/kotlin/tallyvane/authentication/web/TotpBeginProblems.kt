package tallyvane.authentication.web

import tallyvane.authentication.application.TotpBegun
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way beginning to turn TOTP on can fail means over HTTP.
 */
internal class TotpBeginProblems : Problems<TotpBegun.Failed> {
    override fun Answers.of(failure: TotpBegun.Failed): Problem = when (failure) {
        is TotpBegun.Failed.AlreadyActive -> conflicting("TOTP is already on. Turn it off to set it up again.")
    }

    override fun toString(): String = "TotpBeginProblems"
}
