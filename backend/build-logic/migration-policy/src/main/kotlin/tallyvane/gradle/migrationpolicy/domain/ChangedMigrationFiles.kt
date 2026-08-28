package tallyvane.gradle.migrationpolicy.domain

/**
 * Which migration files a release adds or changes, relative to some base.
 *
 * [MigrationPolicy] depends on this port, not on git: the question "what changed" is answered
 * once, by [tallyvane.gradle.migrationpolicy.infrastructure.GitChangedMigrationFiles] in
 * production, so the policy itself can be tested against a handwritten list instead of a real
 * repository and a real commit history.
 */
internal interface ChangedMigrationFiles {
    /**
     * @param baseRef Any ref `git diff` accepts as one side of a comparison — a commit SHA, a
     * branch name, a tag.
     * @return Migration files added or modified between [baseRef] and HEAD, read at HEAD.
     * Deleted files are omitted: a migration this release removes is not one it adds a
     * destructive statement to.
     */
    fun changedSince(baseRef: String): List<MigrationFile>
}
