package tallyvane.gradle.verification

import org.gradle.api.Plugin
import org.gradle.api.Project

class VerificationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val arch = target.tasks.register("arch", ArchTaskConvention())
        val check = target.tasks.register("check", CheckDependsOnArch())
        target.gradle.projectsEvaluated {
            val leaves =
                target.allprojects.filter { project ->
                    project.path != ":" && project.subprojects.isEmpty()
                }
            val analyzed = leaves.filter { project -> project.tasks.findByName("ktlintCheck") != null }
            arch.configure(ArchLeafAnalysis(analyzed))
            val checked = leaves.filter { project -> project.tasks.findByName("check") != null }
            check.configure(CheckLeafVerification(checked))
        }
        val buildLogic = runCatching { target.gradle.includedBuild("build-logic") }.getOrNull()
        if (buildLogic != null) {
            arch.configure(ArchBuildLogicCheck(buildLogic))
        }
    }
}
