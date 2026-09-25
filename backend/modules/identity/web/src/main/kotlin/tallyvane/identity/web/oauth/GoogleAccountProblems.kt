package tallyvane.identity.web.oauth

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal interface GoogleAccountProblems : Problems<GoogleAccountProblems.Failure> {
    sealed interface Failure : tallyvane.platform.kernel.Failure {
        data object InvalidCredential : Failure
        data object LastSignInMethod : Failure
        data object NotLinked : Failure
    }

    class Table : GoogleAccountProblems {
        override fun Answers.of(failure: Failure): Problem = when (failure) {
            is Failure.InvalidCredential -> unauthorized("The presented credential was not accepted")
            is Failure.LastSignInMethod -> conflicting("Add another sign-in method before removing Google")
            is Failure.NotLinked -> missing("No Google account is linked")
        }
    }
}
