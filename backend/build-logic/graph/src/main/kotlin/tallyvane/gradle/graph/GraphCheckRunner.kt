package tallyvane.gradle.graph

internal interface GraphCheckRunner {
    fun runAll(checks: List<GraphCheck>): List<Finding>

    class Base(
        private val yaml: ModulesYaml,
        private val projects: IncludedProjects,
    ) : GraphCheckRunner {
        fun runAll(): List<Finding> = runAll(DEFAULT_CHECKS)

        override fun runAll(checks: List<GraphCheck>): List<Finding> =
            checks.flatMap { check -> check.findings(yaml, projects) }

        private companion object {
            val DEFAULT_CHECKS: List<GraphCheck> = listOf(
                GraphCheck.Planned,
                GraphCheck.Platforms,
                GraphCheck.Features,
                GraphCheck.Banned(),
            )
        }
    }
}
