package tallyvane.sessions.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.sessions.application.LifetimeVersionPreviewed

/**
 * What each way previewing a version of the lifetimes can fail means over HTTP.
 */
internal class LifetimePreviewProblems : Problems<LifetimeVersionPreviewed.Failed> {
    override fun Answers.of(failure: LifetimeVersionPreviewed.Failed): Problem = when (failure) {
        is LifetimeVersionPreviewed.Failed.NotAnAdministrator -> forbidden("Only an administrator may read this.")
        is LifetimeVersionPreviewed.Failed.Invalid -> {
            val heard = FaultFields().hearing(failure::writeTo)
            invalid(heard.errors(), heard.detail())
        }
        is LifetimeVersionPreviewed.Failed.NoSuchVersion -> missing("No such version is kept for that client.")
    }

    override fun toString(): String = "LifetimePreviewProblems"
}
