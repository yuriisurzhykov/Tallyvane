package tallyvane.gradle.graph

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import tallyvane.gradle.graph.tasks.ValidateModuleGraphTask

class ModuleGraphPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val task =
            target.tasks.register(
                "validateModuleGraph",
                ValidateModuleGraphTask::class.java,
                Action { graph ->
                    graph.manifestFile.convention(target.layout.projectDirectory.file("modules.yaml"))
                },
            )
        target.gradle.projectsEvaluated {
            val projects = IncludedProjects.Snapshot(target)
            task.configure(Action { graph -> graph.accept(projects) })
        }
    }
}
