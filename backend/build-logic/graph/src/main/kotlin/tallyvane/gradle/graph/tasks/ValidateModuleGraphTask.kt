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
import tallyvane.gradle.graph.GraphCheckRunner
import tallyvane.gradle.graph.IncludedProjects
import tallyvane.gradle.graph.ModulesYaml

abstract class ValidateModuleGraphTask :
    DefaultTask(),
    VerificationTask {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:Input
    abstract val projectDependencies: MapProperty<String, List<String>>

    @get:Input
    abstract val includedProjectPaths: ListProperty<String>

    @get:Input
    abstract val coordinates: ListProperty<String>

    init {
        group = "verification"
        description =
            "Fails if the Gradle project graph disagrees with modules.yaml, or if MockK/Mockito appear."
        ignoreFailures = false
    }

    internal fun accept(projects: IncludedProjects) {
        includedProjectPaths.set(projects.paths().toList())
        projectDependencies.set(
            projects.paths().associateWith { path -> projects.dependencies(path).toList() },
        )
        coordinates.set(projects.coordinates().map(::wire))
    }

    @TaskAction
    fun validate() {
        val findings =
            GraphCheckRunner
                .Base(
                    ModulesYaml.File(manifestFile.get().asFile),
                    IncludedProjects.Wired(
                        includedProjectPaths.get().toSet(),
                        projectDependencies.get().mapValues { entry -> entry.value.toSet() },
                        coordinates.get(),
                    ),
                ).runAll()
        if (findings.isNotEmpty()) {
            throw GradleException(
                findings.joinToString(
                    separator = "\n",
                    prefix = "Module graph is not the manifest:\n",
                ),
            )
        }
    }

    private fun wire(coordinate: IncludedProjects.Coordinate): String =
        "${coordinate.projectPath()}\t${coordinate.group()}"
}
