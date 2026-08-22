package tallyvane.gradle.graph.domain

/**
 * One disagreement between the declared graph in `modules.yaml` and the
 * Gradle build that [GraphCheck] reports.
 *
 * A finding is the operator-facing message and nothing else: no severity,
 * code, or structured payload. The Gradle task joins [toString] values
 * with newlines into a `GradleException`. An empty list of findings means
 * that check — or the whole use case — is clean.
 *
 * @param text Exact line printed to the Gradle log. Must already name the
 * remedy (move an entry, remove an edge, add a declared dependency), not
 * only the observation. Equality and hashing are on this string, so two
 * findings with the same text are the same finding.
 */
internal data class Finding(
    private val text: String,
) {
    /**
     * The Gradle log line for this finding.
     *
     * Overrides the data-class `Finding(text=…)` form so the task output
     * stays the message itself.
     */
    override fun toString(): String = text
}
