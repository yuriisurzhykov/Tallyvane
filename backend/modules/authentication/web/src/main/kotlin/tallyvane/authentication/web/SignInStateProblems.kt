package tallyvane.authentication.web

import tallyvane.authentication.application.SignInShown
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way asking where a sign-in stands can fail means over HTTP.
 */
internal class SignInStateProblems : Problems<SignInShown.Failed> {
    override fun Answers.of(failure: SignInShown.Failed): Problem = when (failure) {
        is SignInShown.Failed.NoSignIn -> missing("There is no sign-in or confirmation to look at.")
    }

    override fun toString(): String = "SignInStateProblems"
}
