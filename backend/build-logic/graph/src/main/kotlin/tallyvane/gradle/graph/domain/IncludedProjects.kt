package tallyvane.gradle.graph.domain

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * The included Gradle project graph as [GraphCheck] sees it: leaf paths,
 * compile project-dependencies of those leaves, and external coordinates.
 *
 * Configuration cache cannot serialise a live [Project]. [Snapshot] walks
 * the build while configuring; [Wired] rebuilds the same view from the
 * flat `@Input` values the task stored. Callers depend on this interface
 * and must not assume which implementation they hold.
 *
 * This is the only domain type that imports Gradle, because [Snapshot] and
 * [Wired] belong on the same port.
 */
internal interface IncludedProjects {
    /**
     * Gradle paths of leaf projects.
     *
     * @return Paths such as `:platform:kernel` and `:modules:jobs:domain`.
     * Parent projects (`:`, `:platform`, `:modules:jobs`) are omitted:
     * they have no `modules.yaml` row to disagree with.
     */
    fun paths(): Set<String>

    /**
     * Project-dependency paths of one leaf on every declaring configuration.
     *
     * Test configurations count, because a test edge is access too: §15.2 asks
     * this task to resolve the real Gradle graph, and a dependency reached only
     * from `src/test` is still one module reaching another. A test that needs an
     * edge its module may not declare is a test in the wrong layer — an
     * integration test of a use case belongs to `infrastructure`, where
     * `platform:*` is already allowed, not to `application`, where it is not.
     *
     * @param path Gradle path of a leaf that [paths] may or may not contain.
     * @return Paths this project depends on via `api`, `implementation`,
     * `compileOnly`, their `test`, `integrationTest` and `testFixtures`
     * counterparts. Empty if [path] is unknown or has no such edges. Runtime
     * configurations are ignored: they add no compile-time access.
     */
    fun dependencies(path: String): Set<String>

    /**
     * External module coordinates declared on any configuration of a leaf,
     * including test.
     *
     * @return One [Coordinate] per external dependency. [GraphCheck.Banned]
     * matches on [Coordinate.group].
     */
    fun coordinates(): List<Coordinate>

    /**
     * One external dependency of one leaf, reduced to the two fields
     * [GraphCheck.Banned] needs.
     *
     * @property projectPath Gradle path of the leaf that declared the
     * dependency (`:arch-tests`).
     * @property group Maven group (`io.mockk`). Nested groups such as
     * `org.mockito.kotlin` are stored as written; the ban check also
     * matches prefixes (`org.mockito.`).
     */
    data class Coordinate(
        val projectPath: String,
        val group: String,
    )

    /**
     * [IncludedProjects] captured from a live Gradle build at configuration
     * time.
     *
     * Reads [root.allprojects] once and never again, so later evaluation
     * cannot change the snapshot the task serialises.
     *
     * @param root Root project of the build being checked (the backend
     * included-build consumer). Leaves are every project except `:` that
     * has no subprojects.
     */
    class Snapshot(
        root: Project,
    ) : IncludedProjects {
        private val captured = Captured(root)

        override fun paths(): Set<String> = captured.paths

        override fun dependencies(path: String): Set<String> = captured.dependencies[path].orEmpty()

        override fun coordinates(): List<Coordinate> = captured.coordinates

        /**
         * Eager copy of [root]'s leaf graph.
         *
         * @param root Same root passed to [Snapshot].
         */
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
                dependencies = leaves.associate { project -> project.path to declaredProjectPaths(project) }
                coordinates = leaves.flatMap(::coordinates)
            }

            /**
             * Self-references are dropped: consuming one's own `testFixtures`
             * registers as a project dependency on oneself, and a module is not
             * reaching another module by using its own fixtures.
             */
            private fun declaredProjectPaths(project: Project): Set<String> {
                val declaring = CHECKED_CONFIGS.mapNotNull { name -> project.configurations.findByName(name) }
                return declaring
                    .flatMap { configuration ->
                        configuration.dependencies.withType(ProjectDependency::class.java).map { it.path }
                    }.filterNot { path -> path == project.path }
                    .toSet()
            }

            private fun coordinates(project: Project): List<Coordinate> =
                project.configurations.flatMap { configuration ->
                    configuration.dependencies.withType(ExternalModuleDependency::class.java).map { dependency ->
                        Coordinate(project.path, dependency.group)
                    }
                }

            private companion object {
                const val ROOT = ":"

                val CHECKED_CONFIGS =
                    listOf(
                        "api",
                        "implementation",
                        "compileOnly",
                        "testImplementation",
                        "testCompileOnly",
                        "integrationTestImplementation",
                        "integrationTestCompileOnly",
                        "testFixturesApi",
                        "testFixturesImplementation",
                        "testFixturesCompileOnly",
                    )
            }
        }
    }

    /**
     * [IncludedProjects] rebuilt from the serialisable `@Input` values the
     * task stored during configuration.
     *
     * @param included Leaf Gradle paths; becomes [paths].
     * @param dependencies Leaf path to its compile project-dependency paths;
     * a missing key is treated as no dependencies.
     * @param wiredCoordinates External coordinates as `path<TAB>group`
     * strings. Each entry must contain a tab; the path is before it, the
     * group after it.
     * @throws IllegalStateException from [coordinates] if a wire string has
     * no tab.
     */
    class Wired(
        private val included: Set<String>,
        private val dependencies: Map<String, Set<String>>,
        private val wiredCoordinates: List<String>,
    ) : IncludedProjects {
        override fun paths(): Set<String> = included

        override fun dependencies(path: String): Set<String> = dependencies[path].orEmpty()

        override fun coordinates(): List<Coordinate> = wiredCoordinates.map(::coordinate)

        /**
         * Decodes one `path<TAB>group` wire.
         *
         * @param value Task `@Input` string written by `ValidateModuleGraphTask`.
         * @throws IllegalStateException if [value] contains no tab.
         */
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
