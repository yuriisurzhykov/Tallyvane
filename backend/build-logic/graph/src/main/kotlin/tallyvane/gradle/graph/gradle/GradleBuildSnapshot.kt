package tallyvane.gradle.graph.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import tallyvane.gradle.graph.rules.DeclaredCoordinate
import tallyvane.gradle.graph.rules.ProjectGraph

internal class GradleBuildSnapshot(
    private val root: Project,
) {
    fun capture(): ProjectGraph {
        val leaves =
            root.allprojects.filter { project ->
                project.path != ROOT && project.subprojects.isEmpty()
            }
        return ProjectGraph(
            included = leaves.map { it.path }.toSet(),
            dependencies = leaves.associate { project -> project.path to compileProjectPaths(project) },
            coordinates = leaves.flatMap(::coordinates),
        )
    }

    private fun compileProjectPaths(project: Project): Set<String> {
        val compile = COMPILE_CONFIGS.mapNotNull { name -> project.configurations.findByName(name) }
        return compile
            .flatMap { configuration ->
                configuration.dependencies.withType(ProjectDependency::class.java).map { it.path }
            }.toSet()
    }

    private fun coordinates(project: Project): List<DeclaredCoordinate> =
        project.configurations.flatMap { configuration ->
            configuration.dependencies.withType(ExternalModuleDependency::class.java).map { dependency ->
                DeclaredCoordinate(project.path, dependency.group)
            }
        }

    private companion object {
        const val ROOT = ":"
        val COMPILE_CONFIGS = listOf("api", "implementation", "compileOnly")
    }
}
