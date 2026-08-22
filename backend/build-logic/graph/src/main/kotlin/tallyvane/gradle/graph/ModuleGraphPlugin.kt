package tallyvane.gradle.graph

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import tallyvane.gradle.graph.gradle.GradleBuildSnapshot
import tallyvane.gradle.graph.gradle.ValidateModuleGraphTask

class ModuleGraphPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val task =
            target.tasks.register(
                "validateModuleGraph",
                ValidateModuleGraphTask::class.java,
                Action { graph ->
                    graph.group = "verification"
                    graph.description =
                        "Fails if the Gradle project graph disagrees with modules.yaml, or if MockK/Mockito appear."
                    graph.manifestFile.convention(target.layout.projectDirectory.file("modules.yaml"))
                },
            )
        target.gradle.projectsEvaluated {
            val snapshot = GradleBuildSnapshot(target).capture()
            task.configure(Action { graph -> graph.accept(snapshot) })
        }
    }
}
