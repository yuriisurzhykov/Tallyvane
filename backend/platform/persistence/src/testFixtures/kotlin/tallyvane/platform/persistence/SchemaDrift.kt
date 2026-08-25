package tallyvane.platform.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

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
 * declares. Drift runs both ways, and a gate that watched one direction would pass the
 * other half of the mistakes.
 *
 * ### What it cannot see
 *
 * Only the tables it is given. "Unmapped" is meaningful only against a complete set, so a
 * partial list makes a neighbour's table look like something to drop. That is why the run
 * over every table belongs to the composition root, the one place allowed to see all of
 * them, and not to a platform module that may not depend on `modules:*` at all.
 *
 * On PostgreSQL, sequences created by hand rather than by a registered column are outside
 * its checks.
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
     * @return empty when the schema and the Kotlin declarations agree. Anything else is
     * drift, and each entry names what differs — including `DROP` for objects the database
     * has and no table declares.
     */
    public fun from(vararg tables: Table): List<String> {
        val database = Database.connect(access.url, user = access.user, password = access.password)
        return transaction(database) {
            MigrationUtils.statementsRequiredForDatabaseMigration(tables = tables, withLogs = false)
        }
    }
}
