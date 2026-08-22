package tallyvane.gradle.verification

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task

internal class ArchLeafAnalysis(
    private val analyzed: List<Project>,
) : Action<Task> {
    override fun execute(task: Task) {
        task.dependsOn(analyzed.map { project -> project.tasks.named("ktlintCheck") })
        task.dependsOn(analyzed.map { project -> project.tasks.named("detekt") })
    }
}
