package tallyvane.gradle.graph.application

import tallyvane.gradle.graph.domain.Finding
import tallyvane.gradle.graph.domain.GraphCheck
import tallyvane.gradle.graph.domain.IncludedProjects
import tallyvane.gradle.graph.domain.ModulesYaml

/**
 * Runs the graph checks and returns their findings.
 *
 * This is the use case the Gradle task calls: given a declared graph and
 * an included build, concatenate every [GraphCheck]. An empty list means
 * the build matches `modules.yaml` and has no banned coordinates. The
 * task turns a non-empty list into a failed build; this class does not
 * throw.
 *
 * @param yaml Declared graph (`planned:`, `platform:`, `modules:`, `layers:`).
 * @param projects Included leaf projects, their compile project-dependencies,
 * and external coordinates.
 * @param checks Checks to run, in order. Production uses Planned, Unlisted,
 * Platforms, Features, then Banned. A test may pass a shorter list; an empty
 * list always yields no findings.
 */
internal class ValidateModuleGraph(
    private val yaml: ModulesYaml,
    private val projects: IncludedProjects,
    private val checks: List<GraphCheck> = DEFAULT_CHECKS,
) {
    /**
     * Concatenation of [checks], each called with the constructor's [yaml]
     * and [projects].
     *
     * @return Findings in check-declaration order, then in each check's
     *   discovery order. Empty means every check is clean.
     */
    fun findings(): List<Finding> =
        checks.flatMap { check -> check.findings(yaml, projects) }

    private companion object {
        val DEFAULT_CHECKS: List<GraphCheck> =
            listOf(
                GraphCheck.Planned,
                GraphCheck.Unlisted,
                GraphCheck.Platforms,
                GraphCheck.Features,
                GraphCheck.Banned(),
            )
    }
}
