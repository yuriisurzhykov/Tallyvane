package tallyvane.gradle.root

import org.gradle.api.Plugin
import org.gradle.api.Project
import tallyvane.gradle.graph.ModuleGraphPlugin
import tallyvane.gradle.migrationpolicy.MigrationPolicyPlugin
import tallyvane.gradle.verification.VerificationPlugin

class TallyvaneRootPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(ModuleGraphPlugin::class.java)
        target.pluginManager.apply(MigrationPolicyPlugin::class.java)
        target.pluginManager.apply(VerificationPlugin::class.java)
    }
}
