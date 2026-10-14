package tallyvane.authentication.web

import tallyvane.authentication.application.PolicyVersionActivated
import tallyvane.platform.http.FieldError
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way putting a version in force can fail means over HTTP.
 *
 * Putting in force a version of the administrators' sign-in that none of them could pass is `422`, not `409`: the
 * version is fine and the state has not moved, the request asks for something the rules refuse. A `409` carries no
 * code, and the code is what lets a screen say so.
 */
internal class PolicyActivationProblems : Problems<PolicyVersionActivated.Failed> {
    override fun Answers.of(failure: PolicyVersionActivated.Failed): Problem = when (failure) {
        is PolicyVersionActivated.Failed.NotAnAdministrator -> forbidden("Only an administrator may do this.")
        is PolicyVersionActivated.Failed.Invalid -> {
            val heard = FaultFields().hearing(failure::writeTo)
            invalid(heard.errors(), heard.detail())
        }
        is PolicyVersionActivated.Failed.NoSuchVersion -> missing("No such version is kept for that purpose.")
        is PolicyVersionActivated.Failed.WouldLockOutEveryAdministrator -> invalid(
            listOf(FieldError("number", "would-lock-out-every-administrator")),
            "No administrator could sign in under this version, so it is not put in force.",
        )
    }

    override fun toString(): String = "PolicyActivationProblems"
}
