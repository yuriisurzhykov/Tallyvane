package tallyvane.gradle.verification

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.initialization.IncludedBuild

internal class ArchBuildLogicCheck(
    private val buildLogic: IncludedBuild,
) : Action<Task> {
    override fun execute(task: Task) {
        task.dependsOn(buildLogic.task(":check"))
    }
}
