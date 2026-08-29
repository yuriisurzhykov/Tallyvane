package tallyvane.gradle.migrationpolicy.domain

/**
 * One migration file [MigrationPolicy] considers, as of HEAD — added or changed relative to a
 * base ref by [tallyvane.gradle.migrationpolicy.infrastructure.GitChangedMigrationFiles], or a
 * handwritten fixture in a test.
 *
 * @property path Repository-relative path, used only for the message a [Finding] carries.
 * @property content The file's full text at HEAD, not at the base ref — a destructive statement
 * removed again before HEAD is not something ADR-066 has anything left to say about.
 */
internal data class MigrationFile(
    val path: String,
    val content: String,
)
