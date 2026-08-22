package tallyvane.gradle.graph

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import tallyvane.gradle.graph.domain.IncludedProjects
import tallyvane.gradle.graph.tasks.ValidateModuleGraphTask

/**
 * Composition root of `tallyvane.graph`.
 *
 * Registers the `validateModuleGraph` task, defaults its manifest to
 * `modules.yaml` in [Project.getLayout]'s project directory, and after
 * project evaluation writes an [IncludedProjects.Snapshot] onto the task.
 * Holds no comparison logic: that lives in
 * [tallyvane.gradle.graph.application.ValidateModuleGraph].
 *
 * Apply with `id("tallyvane.graph")` or via `tallyvane.root`. Do not
 * instantiate this class from a build script.
 */
class ModuleGraphPlugin : Plugin<Project> {

    /**
     * Registers and wires `validateModuleGraph` on [target].
     *
     * Snapshotting waits for `projectsEvaluated` so every included leaf
     * exists before [IncludedProjects.Snapshot] walks `allprojects`.
     *
     * @param target Project the plugin is applied to (backend root).
     */
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
