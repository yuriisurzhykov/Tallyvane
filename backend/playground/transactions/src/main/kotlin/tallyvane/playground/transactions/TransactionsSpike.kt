package tallyvane.playground.transactions

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresPersistence
import java.sql.DriverManager

private object SpikeRows : Table("spike_rows") {
    val n = integer("n")
}

// Which step wrote the row, so the table itself shows what survived.
private const val BY_COMMIT = 1
private const val BY_ROLLBACK = 2
private const val BY_FAILURE = 3
private const val BY_NESTING = 4

private val access =
    DatabaseAccess(
        url = System.getProperty("spike.url", "jdbc:postgresql://localhost:5433/demo"),
        user = System.getProperty("spike.user", "demo"),
        password = Secret(System.getProperty("spike.password", "demo")),
    )

private fun sql(statement: String) {
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
        connection.createStatement().use { it.execute(statement) }
    }
}

/**
 * Counted over its own connection, so it sees committed rows only - never the ones a
 * transaction is still holding.
 */
private fun committed(): Int =
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("select count(*) from spike_rows").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

private fun step(title: String) {
    println()
    println("=== $title")
    println("  committed rows before: ${committed()}")
}

private fun outcome(sentence: String) {
    println("  committed rows after:  ${committed()}  <- $sentence")
}

/**
 * What a "dumb" repository method looks like: a plain Exposed DSL call, no
 * `transaction { }` of its own. Whether this needs an ambient transaction already
 * open — and whether it correctly joins one rather than opening a second — is
 * exactly srez 14's open question.
 */
private fun dumbRead(): Int = SpikeRows.selectAll().count().toInt()

private fun dumbInsert(value: Int) {
    SpikeRows.insert { it[n] = value }
}

fun main(): Unit =
    runBlocking {
        println("Connecting to ${access.url} as ${access.user}")
        sql("create table if not exists spike_rows (n integer not null)")
        sql("truncate table spike_rows")

        PostgresPersistence(access).use { persistence ->
            val transactions = persistence.transactions

            step("commit: one write, verdict Commit")
            transactions.inTransaction {
                SpikeRows.insert { it[n] = BY_COMMIT }
                println("  inside the transaction, rows visible to it: ${SpikeRows.selectAll().count()}")
                Verdict.Commit(Unit)
            }
            outcome("the write survived")

            step("rollback: one write, verdict Rollback")
            transactions.inTransaction {
                SpikeRows.insert { it[n] = BY_ROLLBACK }
                println("  inside the transaction, rows visible to it: ${SpikeRows.selectAll().count()}")
                println("  the write really happened; now the verdict is Rollback")
                Verdict.Rollback(Unit)
            }
            outcome("the write was undone")

            step("failure: one write, then an exception")
            try {
                transactions.inTransaction<Unit> {
                    SpikeRows.insert { it[n] = BY_FAILURE }
                    println("  inside the transaction, rows visible to it: ${SpikeRows.selectAll().count()}")
                    error("deliberate failure")
                }
            } catch (cause: IllegalStateException) {
                println("  the exception reached the caller: ${cause.message}")
            }
            outcome("the write was undone")

            step("nesting: a write, then inTransaction inside inTransaction")
            try {
                transactions.inTransaction {
                    SpikeRows.insert { it[n] = BY_NESTING }
                    println("  inside the transaction, rows visible to it: ${SpikeRows.selectAll().count()}")
                    transactions.inTransaction { Verdict.Commit(Unit) }
                    Verdict.Commit(Unit)
                }
            } catch (cause: IllegalStateException) {
                println("  refused: ${cause.message}")
            }
            outcome("the refusal took the outer write down with it")

            println()
            println("=== a 'dumb' method (no transaction { } of its own) called with none open")
            try {
                val count = dumbRead()
                println("  did NOT throw: read $count rows with no ambient transaction")
            } catch (cause: Exception) {
                println("  threw ${cause::class.simpleName}: ${cause.message}")
            }

            println()
            println("=== the same 'dumb' method called from inside transactions.inTransaction")
            val readInside = transactions.inTransaction {
                val count = dumbRead()
                Verdict.Commit(count)
            }
            println("  did NOT throw: read $readInside rows, using the ambient transaction TransactionRunner opened")

            println()
            println("=== one use case, one inTransaction, covering a dumb read AND a dumb write")
            step("read-then-write: mirrors GoogleSignInCompleter.findOrCreateUser's real shape")
            transactions.inTransaction {
                val existing = dumbRead()
                println("  dumb read, from inside the same block that will also write: $existing rows so far")
                dumbInsert(BY_READ_THEN_WRITE)
                println("  dumb write, same block, same ambient transaction: ${dumbRead()} rows now visible to it")
                Verdict.Commit(Unit)
            }
            outcome("read and write shared one transaction, no repository opened its own")
        }
        println()
        println("Pool closed. Two rows should remain: the ones the commit and the read-then-write step wrote.")
    }

private const val BY_READ_THEN_WRITE = 5
