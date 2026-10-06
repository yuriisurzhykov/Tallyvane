package tallyvane.journal.web

import tallyvane.journal.application.ActivityOutcome
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

/**
 * What each way reading the journal can fail means over HTTP.
 */
internal class ActivityProblems : Problems<ActivityOutcome.Failed> {
    override fun Answers.of(failure: ActivityOutcome.Failed): Problem = when (failure) {
        is ActivityOutcome.Failed.UnknownCursor -> malformed("The cursor is not one a page of the journal gave.")
    }

    override fun toString(): String = "ActivityProblems"
}
