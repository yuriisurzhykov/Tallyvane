package tallyvane.gradle.migrationpolicy.domain

/**
 * ADR-066 as a predicate: which [MigrationFile]s a release may ship, expressed as the
 * [Finding]s that make one not allowed.
 *
 * Holds no I/O — which files count as "added or changed" is
 * [tallyvane.gradle.migrationpolicy.infrastructure.GitChangedMigrationFiles]'s question, not
 * this one. This class only reads content already handed to it, which is what lets
 * `MigrationPolicySpec` assert against handwritten strings instead of a real git repository.
 */
internal class MigrationPolicy {
    /**
     * @param files Migration files a release added or changed, in any order.
     * @return One [Finding] per forbidden statement found, across every file. Empty when every
     * file is additive.
     */
    fun findings(files: List<MigrationFile>): List<Finding> = files.flatMap(::findings)

    private fun findings(file: MigrationFile): List<Finding> {
        val lines = withoutSqlComments(file.content).lines()
        return lines.withIndex().flatMap { (index, line) -> findings(file.path, lineNumber = index + 1, line = line) }
    }

    private fun findings(path: String, lineNumber: Int, line: String): List<Finding> =
        ForbiddenStatement.entries
            .filter { statement -> statement.matches(line) }
            .map { statement ->
                Finding(
                    "$path:$lineNumber: ${statement.label} is not additive — split into an " +
                        "expand release and a later contract release (ADR-066)",
                )
            }
}
