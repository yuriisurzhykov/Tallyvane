package tallyvane.sessions.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.sessions.application.LifetimeVersionAdded

/**
 * What each way adding a version of the lifetimes can fail means over HTTP.
 *
 * Lifetimes outside the bounds are `422` with one error for each bound they break, so a form is corrected once.
 */
internal class LifetimeVersionProblems : Problems<LifetimeVersionAdded.Failed> {
    override fun Answers.of(failure: LifetimeVersionAdded.Failed): Problem = when (failure) {
        is LifetimeVersionAdded.Failed.NotAnAdministrator -> forbidden("Only an administrator may do this.")
        is LifetimeVersionAdded.Failed.Invalid -> {
            val heard = FaultFields().hearing(failure::writeTo)
            invalid(heard.errors(), heard.detail())
        }
    }

    override fun toString(): String = "LifetimeVersionProblems"
}
