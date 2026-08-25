package tallyvane.arch

import java.io.File

/**
 * Every schema a migration names that is not the schema its module owns, with
 * foreign-key clauses left out.
 *
 * §4.6 draws the line exactly there: "Внешние ключи между схемами разрешены,
 * соединения — нет." So `references identity.users (id)` is legal and
 * `join jobs.companies` is not, in the same file.
 *
 * This exists because the Kotlin gates cannot see it. `own-schema-only` and
 * `no-cross-schema-join` read Kotlin, and the tempting way to reach a
 * neighbour's tables is a `create view` in SQL — where a module would depend on
 * another's column names with nothing to stop it.
 *
 * A qualified name only counts where a schema may actually stand: after `from`,
 * `join`, `table` and their siblings. Matching every `x.y` would report table
 * aliases — `a.id` in `from applications.applications a` is not a schema, and a
 * first draft of this function said it was.
 */
internal fun foreignSchemasIn(sql: String, ownSchema: String): List<String> {
    val statements = withoutForeignKeyClauses(withoutSqlComments(sql))
    return SCHEMA_POSITIONS
        .flatMap { pattern -> pattern.findAll(statements).map { it.groupValues[1].lowercase() } }
        .filter { it != ownSchema && it !in SQL_SCHEMA_ALLOW }
        .distinct()
}

/**
 * Migration files under `platform/` and `modules/`, paired with the schema whose
 * tables they may name. A capability owns the schema named after it; every
 * platform module writes into the single `platform` schema (§8.9).
 */
internal fun migrationFiles(root: File): List<Pair<File, String>> = listOf("platform", "modules")
    .map { File(root, it) }
    .filter { it.isDirectory }
    .flatMap { top -> top.walkTopDown().filter { it.isFile && it.extension.equals("sql", ignoreCase = true) } }
    .mapNotNull { file -> ownSchemaOf(file)?.let { file to it } }

private fun ownSchemaOf(file: File): String? {
    val path = file.invariantSeparatorsPath
    if (path.contains("/platform/")) {
        return "platform"
    }
    return Regex("""/modules/([^/]+)/""").find(path)?.groupValues?.get(1)?.lowercase()
}

private fun withoutSqlComments(sql: String): String = sql
    .replace(Regex("""--[^\n]*"""), " ")
    .replace(Regex("""/\*[\s\S]*?\*/"""), " ")

private fun withoutForeignKeyClauses(sql: String): String =
    sql.replace(Regex("""references\s+[a-z_][a-z0-9_]*\.[a-z0-9_]+""", RegexOption.IGNORE_CASE), " ")

private val SCHEMA_POSITIONS =
    listOf(
        Regex(
            """\b(?:from|join|into|update|truncate|table|view)\s+(?:only\s+)?([a-z_][a-z0-9_]*)\.[a-z_][a-z0-9_]*""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\bindex\b[^;]*?\bon\s+([a-z_][a-z0-9_]*)\.[a-z_][a-z0-9_]*""", RegexOption.IGNORE_CASE),
    )

// Not neighbours: Postgres' own catalogue and the default namespace.
private val SQL_SCHEMA_ALLOW = setOf("public", "pg_catalog", "information_schema", "pg_temp")
