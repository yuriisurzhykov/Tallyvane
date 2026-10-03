package tallyvane.authentication.web

import tallyvane.authentication.application.TotpDisabled
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way turning TOTP off can fail means over HTTP.
 */
internal class TotpRemovalProblems : Problems<TotpDisabled.Failed> {
    override fun Answers.of(failure: TotpDisabled.Failed): Problem = when (failure) {
        is TotpDisabled.Failed.NotEnabled -> conflicting("TOTP is not on.")
    }

    override fun toString(): String = "TotpRemovalProblems"
}
