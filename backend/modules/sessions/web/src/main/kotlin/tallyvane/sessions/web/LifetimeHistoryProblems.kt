package tallyvane.sessions.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.sessions.application.LifetimeVersionsListed

/**
 * What each way reading the versions of the lifetimes can fail means over HTTP.
 */
internal class LifetimeHistoryProblems : Problems<LifetimeVersionsListed.Failed> {
    override fun Answers.of(failure: LifetimeVersionsListed.Failed): Problem = when (failure) {
        is LifetimeVersionsListed.Failed.NotAnAdministrator -> forbidden("Only an administrator may read this.")
        is LifetimeVersionsListed.Failed.Invalid -> {
            val heard = FaultFields().hearing(failure::writeTo)
            invalid(heard.errors(), heard.detail())
        }
    }

    override fun toString(): String = "LifetimeHistoryProblems"
}
