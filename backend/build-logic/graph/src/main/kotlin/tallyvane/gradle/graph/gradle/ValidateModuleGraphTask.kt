package tallyvane.gradle.graph.gradle

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
import tallyvane.gradle.graph.manifest.YamlManifestSource
import tallyvane.gradle.graph.rules.ProjectGraph
import tallyvane.gradle.graph.rules.ValidateModuleGraph

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
        ignoreFailures = false
    }

    internal fun accept(snapshot: ProjectGraph) {
        includedProjectPaths.set(snapshot.included.toList())
        projectDependencies.set(snapshot.dependencies.mapValues { it.value.toList() })
        coordinates.set(snapshot.coordinates.map { coordinate -> CoordinateWire().encode(coordinate) })
    }

    @TaskAction
    fun validate() {
        val findings =
            ValidateModuleGraph().invoke(
                YamlManifestSource(manifestFile.get().asFile).load(),
                snapshot(),
            )
        if (findings.isNotEmpty()) {
            throw GradleException(
                findings.joinToString(
                    separator = "\n",
                    prefix = "Module graph is not the manifest:\n",
                ) { finding -> finding.message },
            )
        }
    }

    private fun snapshot(): ProjectGraph =
        ProjectGraph(
            included = includedProjectPaths.get().toSet(),
            dependencies = projectDependencies.get().mapValues { it.value.toSet() },
            coordinates = coordinates.get().map { value -> CoordinateWire().decode(value) },
        )
}
