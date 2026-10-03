package tallyvane.authentication.web

import tallyvane.authentication.application.CodesRegenerated
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way asking for new recovery codes can fail means over HTTP.
 */
internal class RecoveryCodeProblems : Problems<CodesRegenerated.Failed> {
    override fun Answers.of(failure: CodesRegenerated.Failed): Problem = when (failure) {
        is CodesRegenerated.Failed.NotActive -> conflicting("TOTP is not on, so there are no codes to replace.")
    }

    override fun toString(): String = "RecoveryCodeProblems"
}
