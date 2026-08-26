package tallyvane.platform.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import java.sql.DriverManager

/**
 * The difference between what the migrations built and what Exposed expects.
 *
 * Tables are declared twice — in SQL for the migration and in Kotlin for Exposed — and
 * nothing makes the two agree. A column added to one and forgotten in the other is not a
 * compile error; it is a failure the first time a query runs, in production, on the path
 * that reads that column. This turns it into a build failure instead.
 *
 * Takes its database in the constructor, like `FlywayMigrations` beside it. An earlier draft
 * was an `object` with one function taking everything as parameters — a `Utils` class under
 * another name, and a shape `no-stateful-objects` already forbids in `main`. That rule runs
 * on the production scope only, which is the sole reason it did not fire: a test fixture is
 * still code, and its quality does not depend on which source set holds it.
 *
 * ### Why `MigrationUtils` and not `SchemaUtils`
 *
 * `SchemaUtils.statementsRequiredToActualizeScheme` is deprecated in Exposed 1.x in favour
 * of this, and would not compile here anyway with warnings as errors. It also answers only
 * half the question: it reports what the database is *missing* — a table to create, a
 * column to add — and says nothing about what the database has that no Kotlin table
 * declares. Drift runs both ways for columns of the tables it is given, and a gate that
 * watched one direction would pass the other half of those mistakes.
 *
 * ### What [from] cannot see
 *
 * Only the tables it is given. Measured 2026-08-25: `statementsRequiredForDatabaseMigration`
 * partitions those objects into existing and missing; it does not enumerate the catalog. A
 * table left in the database after its Kotlin declaration is removed produces no statement,
 * even in the same schema. That is the third side of drift, and it is [unmappedTables].
 *
 * "Unmapped" is meaningful only against a complete set, so a partial list makes a neighbour's
 * table look like something to drop. That is why the run over every table belongs to the
 * composition root, the one place allowed to see all of them, and not to a platform module
 * that may not depend on `modules:*` at all.
 *
 * On PostgreSQL, sequences created by hand rather than by a registered column are outside
 * its checks. An empty leftover schema, with no table inside it, is also outside: this
 * compares tables, not schemas.
 */
public class SchemaDrift(private val access: DatabaseAccess) {
    /**
     * Statements that would be needed to make this database match [tables].
     *
     * Connects per call, and the constructor only assigns. That is not ceremony:
     * `Database.connect` registers the database with Exposed's global `TransactionManager`
     * and makes it the default for any `transaction { }` invoked without an explicit `db`.
     * Doing that while constructing an object would change, invisibly, where an unqualified
     * transaction elsewhere goes — and this is built once per test, each with a database of
     * its own. Every transaction here names its database for the same reason.
     *
     * @return empty when the given tables and the database agree on those tables. Anything
     * else is drift on that set — a missing table, a missing column, a `DROP` for a column
     * the database has and the table does not. Whole tables the database has that [tables]
     * never named are [unmappedTables], not this.
     */
    public fun from(vararg tables: Table): List<String> {
        val database = Database.connect(access.url, user = access.user, password = access.password)
        return transaction(database) {
            MigrationUtils.statementsRequiredForDatabaseMigration(tables = tables, withLogs = false)
        }
    }

    /**
     * Catalog tables that no object in [tables] declares.
     *
     * Reads `information_schema` over JDBC, not through Exposed: the whole point of this
     * method is that Exposed does not walk the catalog. Connects per call for the same
     * reason [from] does — this object is built once per test, each with its own database.
     *
     * `platform.flyway_schema_history` is not drift. Flyway owns that table; no Kotlin
     * `Table` ever will. Ignoring the whole `platform` schema instead would hide a leftover
     * table a platform migration created and later stopped describing.
     *
     * @return qualified names (`schema.table`), empty when every user table is in [tables]
     * or is Flyway's history table.
     */
    public fun unmappedTables(vararg tables: Table): List<String> {
        val declared = declaredKeys(tables)
        return catalogTables()
            .filterNot { it == FLYWAY_HISTORY }
            .filterNot { it in declared }
            .sorted()
    }

    private fun declaredKeys(tables: Array<out Table>): Set<String> = tables
        .map { table ->
            val schema = (table.schemaName ?: PUBLIC_SCHEMA).lowercase()
            val name = table.tableName.substringAfterLast('.').lowercase()
            "$schema.$name"
        }.toSet()

    private fun catalogTables(): List<String> =
        DriverManager.getConnection(access.url, access.user, access.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(CATALOG).use { rows ->
                    buildList {
                        while (rows.next()) {
                            val schema = rows.getString(1).lowercase()
                            val name = rows.getString(2).lowercase()
                            add("$schema.$name")
                        }
                    }
                }
            }
        }

    private companion object {
        const val PUBLIC_SCHEMA = "public"

        const val FLYWAY_HISTORY = "platform.flyway_schema_history"

        const val CATALOG = """
            select table_schema, table_name
            from information_schema.tables
            where table_type = 'BASE TABLE'
              and table_schema not in ('pg_catalog', 'information_schema')
            """
    }
}
