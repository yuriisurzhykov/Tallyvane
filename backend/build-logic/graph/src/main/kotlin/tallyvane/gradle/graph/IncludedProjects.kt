package tallyvane.gradle.graph

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

internal interface IncludedProjects {
    fun paths(): Set<String>

    fun dependencies(path: String): Set<String>

    fun coordinates(): List<Coordinate>

    class Coordinate(
        private val projectPath: String,
        private val group: String,
    ) {
        fun projectPath(): String = projectPath

        fun group(): String = group
    }

    class Snapshot(
        root: Project,
    ) : IncludedProjects {
        private val captured = Captured(root)

        override fun paths(): Set<String> = captured.paths

        override fun dependencies(path: String): Set<String> = captured.dependencies[path].orEmpty()

        override fun coordinates(): List<Coordinate> = captured.coordinates

        private class Captured(
            root: Project,
        ) {
            val paths: Set<String>
            val dependencies: Map<String, Set<String>>
            val coordinates: List<Coordinate>

            init {
                val leaves =
                    root.allprojects.filter { project ->
                        project.path != ROOT && project.subprojects.isEmpty()
                    }
                paths = leaves.map { project -> project.path }.toSet()
                dependencies = leaves.associate { project -> project.path to compileProjectPaths(project) }
                coordinates = leaves.flatMap(::coordinates)
            }

            private fun compileProjectPaths(project: Project): Set<String> {
                val compile = COMPILE_CONFIGS.mapNotNull { name -> project.configurations.findByName(name) }
                return compile
                    .flatMap { configuration ->
                        configuration.dependencies.withType(ProjectDependency::class.java).map { it.path }
                    }.toSet()
            }

            private fun coordinates(project: Project): List<Coordinate> =
                project.configurations.flatMap { configuration ->
                    configuration.dependencies.withType(ExternalModuleDependency::class.java).map { dependency ->
                        Coordinate(project.path, dependency.group)
                    }
                }

            private companion object {
                const val ROOT = ":"
                val COMPILE_CONFIGS = listOf("api", "implementation", "compileOnly")
            }
        }
    }

    class Wired(
        private val included: Set<String>,
        private val dependencies: Map<String, Set<String>>,
        private val wiredCoordinates: List<String>,
    ) : IncludedProjects {
        override fun paths(): Set<String> = included

        override fun dependencies(path: String): Set<String> = dependencies[path].orEmpty()

        override fun coordinates(): List<Coordinate> = wiredCoordinates.map(::coordinate)

        private fun coordinate(value: String): Coordinate {
            val separator = value.indexOf('\t')
            check(separator >= 0) { "Coordinate wire must be path<TAB>group: $value" }
            return Coordinate(
                projectPath = value.substring(0, separator),
                group = value.substring(separator + 1),
            )
        }
    }
}
