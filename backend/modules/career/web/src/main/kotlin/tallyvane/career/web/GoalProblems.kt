package tallyvane.career.web

import tallyvane.career.application.GoalOutcome
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class GoalProblems : Problems<GoalOutcome.Failed> {
    override fun Answers.of(failure: GoalOutcome.Failed): Problem = when (failure) {
        is GoalOutcome.Failed.UnknownRole -> missing("There is no such role in the catalog.")
    }

    override fun toString(): String = "GoalProblems"
}
