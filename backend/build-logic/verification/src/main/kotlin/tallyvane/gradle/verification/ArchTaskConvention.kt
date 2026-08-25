package tallyvane.gradle.verification

import org.gradle.api.Action
import org.gradle.api.Task

internal class ArchTaskConvention : Action<Task> {
    override fun execute(task: Task) {
        task.group = "verification"
        task.description = "ktlint, detekt, modules.yaml graph, and Konsist"
        task.dependsOn("validateModuleGraph")
        task.dependsOn(":arch-tests:test")
    }
}
