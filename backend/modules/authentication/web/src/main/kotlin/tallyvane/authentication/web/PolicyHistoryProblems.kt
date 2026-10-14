package tallyvane.authentication.web

import tallyvane.authentication.application.PolicyVersionsListed
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way reading the versions of a policy can fail means over HTTP.
 */
internal class PolicyHistoryProblems : Problems<PolicyVersionsListed.Failed> {
    override fun Answers.of(failure: PolicyVersionsListed.Failed): Problem = when (failure) {
        is PolicyVersionsListed.Failed.NotAnAdministrator -> forbidden("Only an administrator may read this.")
        is PolicyVersionsListed.Failed.Invalid -> {
            val heard = FaultFields().hearing(failure::writeTo)
            invalid(heard.errors(), heard.detail())
        }
    }

    override fun toString(): String = "PolicyHistoryProblems"
}
