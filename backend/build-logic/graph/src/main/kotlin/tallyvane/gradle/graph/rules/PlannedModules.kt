package tallyvane.gradle.graph.rules

internal class PlannedModules {
    fun findings(
        planned: Set<String>,
        included: Set<String>,
    ): List<GraphFinding> =
        included.mapNotNull { path ->
            finding(planned, path)
        }

    private fun finding(
        planned: Set<String>,
        path: String,
    ): GraphFinding? {
        val module = path.removePrefix(":modules:").substringBefore(":")
        val includedPlan = path.startsWith(":modules:") && module in planned
        return if (includedPlan) {
            GraphFinding(
                "Planned module $module is included as $path — move it from planned: to modules: in modules.yaml",
            )
        } else {
            null
        }
    }
}
