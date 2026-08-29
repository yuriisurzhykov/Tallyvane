package tallyvane.gradle.migrationpolicy.domain

/**
 * One destructive statement [MigrationPolicy] found in a migration this release added or
 * changed.
 *
 * The operator-facing message and nothing else — no severity, code, or structured payload,
 * matching `tallyvane.gradle.graph`'s own `Finding`. The task joins [toString] values with
 * newlines into a `GradleException`. An empty list of findings means the release is clean.
 *
 * @param text Exact line printed to the Gradle log. Names the file, the line, the statement,
 * and the remedy (ADR-066: split into an expand release and a later contract release), not only
 * the observation.
 */
internal data class Finding(
    private val text: String,
) {
    /**
     * The Gradle log line for this finding.
     *
     * Overrides the data-class `Finding(text=…)` form so the task output stays the message
     * itself.
     */
    override fun toString(): String = text
}
