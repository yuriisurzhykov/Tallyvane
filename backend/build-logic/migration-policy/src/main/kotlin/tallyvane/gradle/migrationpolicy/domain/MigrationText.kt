package tallyvane.gradle.migrationpolicy.domain

/**
 * Strips `--` line comments and `/* */` block comments from a migration before
 * [ForbiddenStatement] scans it.
 *
 * Without this, a migration is free to document *why* a later release will need a `DROP` — the
 * exact kind of note ADR-066 asks a contributor to leave — without that documentation tripping
 * the check on its own explanation of one. Nesting is not handled: PostgreSQL migrations in this
 * repository do not nest block comments, and a lexer that did would be a second parser to keep
 * alive for a case nothing here produces.
 *
 * A block comment is replaced by the newlines it contained, not by nothing: collapsing
 * `/* spanning\nthree\nlines */` to the empty string would shift every later line's number up
 * by two, which is exactly the kind of wrong-but-plausible-looking line reference a reader would
 * trust and a reviewer would not think to recount.
 *
 * @param sql One migration file's full text.
 * @return The same text with every comment's characters blanked, so line numbers reported
 * against the result still match the original file.
 */
internal fun withoutSqlComments(sql: String): String {
    val withoutBlockComments =
        BLOCK_COMMENT.replace(sql) { match -> "\n".repeat(match.value.count { char -> char == '\n' }) }
    return withoutBlockComments.lineSequence().joinToString("\n") { line -> line.replace(LINE_COMMENT, "") }
}

private val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")

private val LINE_COMMENT = Regex("""--.*$""")
