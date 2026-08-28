package tallyvane.gradle.migrationpolicy.domain

/**
 * The statements ADR-066 forbids in a migration a blue-green release adds or changes.
 *
 * None of these fail the migration itself — they fail whichever colour is still on the old code
 * and keeps serving traffic through the cutover window, silently, for as long as it does. A
 * destructive change is not banned outright, only deferred: split into an "expand" release (the
 * new shape) and a later "contract" release (removing the old one), once no old colour is left.
 *
 * Written to tolerate the whitespace a formatter or a second developer would add, not to parse
 * SQL — a check that requires exact spacing to catch a `DROP COLUMN` would be worse than one
 * that is merely case-insensitive.
 */
internal enum class ForbiddenStatement(
    val label: String,
    private val pattern: Regex,
) {
    DROP_TABLE("DROP TABLE", Regex("""\bdrop\s+table\b""", RegexOption.IGNORE_CASE)),
    DROP_COLUMN("DROP COLUMN", Regex("""\bdrop\s+column\b""", RegexOption.IGNORE_CASE)),
    SET_NOT_NULL("SET NOT NULL", Regex("""\bset\s+not\s+null\b""", RegexOption.IGNORE_CASE)),
    RENAME_COLUMN("RENAME COLUMN", Regex("""\brename\s+column\b""", RegexOption.IGNORE_CASE)),
    RENAME_TO("RENAME TO", Regex("""\brename\s+to\b""", RegexOption.IGNORE_CASE)),
    ;

    /**
     * @param line One line of a migration, comments already stripped.
     * @return Whether this statement appears anywhere in [line].
     */
    fun matches(line: String): Boolean = pattern.containsMatchIn(line)
}
