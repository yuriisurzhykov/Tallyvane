package tallyvane.sessions.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.sessions.application.LifetimeVersionActivated

/**
 * What each way putting a version of the lifetimes in force can fail means over HTTP.
 */
internal class LifetimeActivationProblems : Problems<LifetimeVersionActivated.Failed> {
    override fun Answers.of(failure: LifetimeVersionActivated.Failed): Problem = when (failure) {
        is LifetimeVersionActivated.Failed.NotAnAdministrator -> forbidden("Only an administrator may do this.")
        is LifetimeVersionActivated.Failed.Invalid -> {
            val heard = FaultFields().hearing(failure::writeTo)
            invalid(heard.errors(), heard.detail())
        }
        is LifetimeVersionActivated.Failed.NoSuchVersion -> missing("No such version is kept for that client.")
    }

    override fun toString(): String = "LifetimeActivationProblems"
}
