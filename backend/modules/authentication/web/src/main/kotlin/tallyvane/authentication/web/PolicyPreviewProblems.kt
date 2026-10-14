package tallyvane.authentication.web

import tallyvane.authentication.application.PolicyVersionPreviewed
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way previewing a version of a policy can fail means over HTTP.
 */
internal class PolicyPreviewProblems : Problems<PolicyVersionPreviewed.Failed> {
    override fun Answers.of(failure: PolicyVersionPreviewed.Failed): Problem = when (failure) {
        is PolicyVersionPreviewed.Failed.NotAnAdministrator -> forbidden("Only an administrator may read this.")
        is PolicyVersionPreviewed.Failed.Invalid -> {
            val heard = FaultFields().hearing(failure::writeTo)
            invalid(heard.errors(), heard.detail())
        }
        is PolicyVersionPreviewed.Failed.NoSuchVersion -> missing("No such version is kept for that purpose.")
    }

    override fun toString(): String = "PolicyPreviewProblems"
}
