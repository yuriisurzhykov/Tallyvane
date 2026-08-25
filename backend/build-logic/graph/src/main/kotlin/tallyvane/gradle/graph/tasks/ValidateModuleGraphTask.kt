package tallyvane.gradle.graph.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import tallyvane.gradle.graph.application.ValidateModuleGraph
import tallyvane.gradle.graph.domain.IncludedProjects
import tallyvane.gradle.graph.domain.ModulesYaml
import tallyvane.gradle.graph.infrastructure.YamlModulesYaml

/**
 * Gradle adapter for [ValidateModuleGraph].
 *
 * Configuration writes serialisable `@Input` values via [accept].
 * Execution rebuilds [IncludedProjects.Wired] and [YamlModulesYaml] from
 * those values so the configuration cache never serialises a `Project`.
 * A non-empty finding list becomes a [GradleException]; an empty list
 * succeeds.
 *
 * Group is `verification`. [ignoreFailures] stays `false`: a disagreeing
 * graph is a failed build, not a warning.
 */
abstract class ValidateModuleGraphTask :
    DefaultTask(),
    VerificationTask {
    /**
     * `modules.yaml` of the project this task is registered on (backend
     * root). Gradle hashes file content, not the path string.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    /**
     * Leaf Gradle path to that project's compile project-dependency paths.
     *
     * Set by [accept] from [IncludedProjects.dependencies]. Keys must be
     * the same paths as [includedProjectPaths].
     */
    @get:Input
    abstract val projectDependencies: MapProperty<String, List<String>>

    /**
     * Leaf Gradle paths included in this build.
     *
     * Set by [accept] from [IncludedProjects.paths].
     */
    @get:Input
    abstract val includedProjectPaths: ListProperty<String>

    /**
     * External coordinates as `path<TAB>group` strings.
     *
     * Set by [accept] from [IncludedProjects.coordinates]. Each string
     * must contain a tab; [IncludedProjects.Wired] rejects any that do not.
     */
    @get:Input
    abstract val coordinates: ListProperty<String>

    init {
        group = "verification"
        description =
            "Fails if the Gradle project graph disagrees with modules.yaml, or if MockK/Mockito appear."
        ignoreFailures = false
    }

    /**
     * Copies [projects] onto the `@Input` properties.
     *
     * Must run at configuration time (`projectsEvaluated`), not from
     * [validate]: execution must not touch a live [org.gradle.api.Project].
     *
     * @param projects Configuration-time snapshot of the included build,
     *   usually [IncludedProjects.Snapshot].
     */
    internal fun accept(projects: IncludedProjects) {
        includedProjectPaths.set(projects.paths().toList())
        projectDependencies.set(
            projects.paths().associateWith { path -> projects.dependencies(path).toList() },
        )
        coordinates.set(projects.coordinates().map(::wire))
    }

    /**
     * Runs [ValidateModuleGraph] and fails the build on any finding.
     *
     * @throws GradleException if findings are non-empty. The message is
     * `Module graph is not the manifest:` followed by each
     * [tallyvane.gradle.graph.domain.Finding] on its own line.
     * @throws IllegalStateException if `modules.yaml` cannot be parsed or
     * a coordinate wire is malformed.
     */
    @TaskAction
    fun validate() {
        val findings = ValidateModuleGraph(manifest(), projects()).findings()
        if (findings.isNotEmpty()) {
            throw GradleException(
                findings.joinToString(
                    separator = "\n",
                    prefix = "Module graph is not the manifest:\n",
                ),
            )
        }
    }

    /**
     * @return [YamlModulesYaml] over [manifestFile].
     */
    private fun manifest(): ModulesYaml = YamlModulesYaml(manifestFile.get().asFile)

    /**
     * @return [IncludedProjects.Wired] from the `@Input` properties written
     *   by [accept].
     */
    private fun projects(): IncludedProjects =
        IncludedProjects.Wired(
            includedProjectPaths.get().toSet(),
            projectDependencies.get().mapValues { entry -> entry.value.toSet() },
            coordinates.get(),
        )

    /**
     * @param coordinate One external module from the configuration-time snapshot.
     * @return `projectPath<TAB>group` for [coordinates].
     */
    private fun wire(coordinate: IncludedProjects.Coordinate): String =
        "${coordinate.projectPath}\t${coordinate.group}"
}
