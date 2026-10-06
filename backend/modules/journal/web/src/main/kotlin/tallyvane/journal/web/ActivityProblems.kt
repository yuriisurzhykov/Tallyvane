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
        is ActivityOutcome.Failed.UnknownCursor -> malformed("The cursor must be a positive whole number.")
    }

    override fun toString(): String = "ActivityProblems"
}
