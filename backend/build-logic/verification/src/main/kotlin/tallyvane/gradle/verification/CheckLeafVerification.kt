package tallyvane.gradle.verification

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Makes the root `check` depend on every leaf's `check`.
 *
 * Without this, running `check` from the backend root only appeared complete:
 * Gradle matches a task name across all projects, so the local invocation picked
 * up each module's tests while CI's `./gradlew arch` did not, and no module test
 * ran on a push. Completeness is now a property of the task graph rather than of
 * how the build was invoked.
 *
 * `integrationTest` is deliberately outside this graph — it is opt-in locally and
 * a separate step in CI.
 */
internal class CheckLeafVerification(
    private val checked: List<Project>,
) : Action<Task> {
    override fun execute(task: Task) {
        task.dependsOn(checked.map { project -> project.tasks.named("check") })
    }
}
