package tallyvane.authentication.web

import tallyvane.authentication.application.TotpConfirmed
import tallyvane.platform.http.FieldError
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way typing the first code can fail means over HTTP.
 */
internal class TotpConfirmationProblems : Problems<TotpConfirmed.Failed> {
    override fun Answers.of(failure: TotpConfirmed.Failed): Problem = when (failure) {
        is TotpConfirmed.Failed.NotBegun -> conflicting("There is no TOTP set-up waiting for a first code.")
        is TotpConfirmed.Failed.WrongCode -> invalid(listOf(FieldError("code", "wrong-code")))
    }

    override fun toString(): String = "TotpConfirmationProblems"
}
