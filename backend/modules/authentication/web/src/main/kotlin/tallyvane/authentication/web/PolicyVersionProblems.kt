package tallyvane.authentication.web

import tallyvane.authentication.application.PolicyVersionAdded
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way adding a version of a policy can fail means over HTTP.
 *
 * A policy outside the bounds is `422` with one error for each bound it breaks, so a form is corrected once.
 */
internal class PolicyVersionProblems : Problems<PolicyVersionAdded.Failed> {
    override fun Answers.of(failure: PolicyVersionAdded.Failed): Problem = when (failure) {
        is PolicyVersionAdded.Failed.NotAnAdministrator -> forbidden("Only an administrator may do this.")
        is PolicyVersionAdded.Failed.Invalid -> {
            val heard = FaultFields().hearing(failure::writeTo)
            invalid(heard.errors(), heard.detail())
        }
    }

    override fun toString(): String = "PolicyVersionProblems"
}
