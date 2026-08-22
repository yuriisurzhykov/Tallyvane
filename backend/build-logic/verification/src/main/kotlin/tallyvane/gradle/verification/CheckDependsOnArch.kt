package tallyvane.gradle.verification

import org.gradle.api.Action
import org.gradle.api.Task

internal class CheckDependsOnArch : Action<Task> {
    override fun execute(task: Task) {
        task.group = "verification"
        task.dependsOn("arch")
    }
}
