package tallyvane.models.web

import tallyvane.models.application.ModelsOutcome
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems
import tallyvane.platform.http.status.Answers

internal class ModelsProblems : Problems<ModelsOutcome.Failed> {
    override fun Answers.of(failure: ModelsOutcome.Failed): Problem = when (failure) {
        is ModelsOutcome.Failed.NotAnAdministrator -> forbidden("Only an administrator manages model providers.")
        is ModelsOutcome.Failed.Invalid -> malformed(failure.reason)
        is ModelsOutcome.Failed.Missing -> missing("There is no such provider.")
        is ModelsOutcome.Failed.NameTaken -> conflicting("Another provider already has this name.")
    }

    override fun toString(): String = "ModelsProblems"
}
