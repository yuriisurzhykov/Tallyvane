package tallyvane.stories.web

import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers
import tallyvane.stories.application.StoryOutcome

internal class StoryProblems : Problems<StoryOutcome.Failed> {
    override fun Answers.of(failure: StoryOutcome.Failed): Problem = when (failure) {
        is StoryOutcome.Failed.Empty -> malformed("Say something: the words are empty.")
        is StoryOutcome.Failed.Missing -> missing("There is no such story or fact.")
        is StoryOutcome.Failed.Unheard -> conflicting("The correction did not say what to change. Nothing changed.")
        is StoryOutcome.Failed.Unavailable -> unavailable(
            "Your words are kept. They will be read when the model is back.",
        )
        is StoryOutcome.Failed.OverBudget -> slowDown("Today's allowance is spent. Your words are kept.")
    }

    override fun toString(): String = "StoryProblems"
}
