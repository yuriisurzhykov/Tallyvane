package tallyvane.playground.savepoints

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresPersistence
import java.sql.DriverManager

private object Emails : Table("spike_emails") {
    val address = text("address").uniqueIndex()
}

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

private fun committedRows(): List<String> =
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("select address from spike_emails order by address").use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

/**
 * What `UserRepository.insert`'s real implementation will do: attempt the insert, catch a unique
 * violation, report an outcome instead of throwing — without a savepoint, PostgreSQL treats any
 * error as poisoning the whole transaction, so a savepoint is set right before the attempt and
 * released or rolled back to depending on the outcome.
 */
private enum class InsertOutcome { INSERTED, EMAIL_TAKEN }

private fun insertGuarded(address: String): InsertOutcome {
    val connection = TransactionManager.current().connection
    val savepoint = connection.setSavepoint("insert_guard")
    return try {
        Emails.insert { it[Emails.address] = address }
        connection.releaseSavepoint(savepoint)
        InsertOutcome.INSERTED
    } catch (cause: ExposedSQLException) {
        connection.rollback(savepoint)
        InsertOutcome.EMAIL_TAKEN
    }
}

fun main(): Unit =
    runBlocking {
        println("Connecting to ${access.url} as ${access.user}")
        sql("create table if not exists spike_emails (address text not null unique)")
        sql("truncate table spike_emails")

        PostgresPersistence(access).use { persistence ->
            val transactions = persistence.transactions

            println()
            println("=== a plain insert, no conflict")
            transactions.inTransaction {
                val outcome = insertGuarded("first@example.com")
                println("  outcome: $outcome, rows visible inside: ${Emails.selectAll().count()}")
                Verdict.Commit(outcome)
            }
            println("  committed rows: ${committedRows()}")

            println()
            println("=== a colliding insert, caught via savepoint, then a plain read in the SAME transaction")
            transactions.inTransaction {
                val outcome = insertGuarded("first@example.com")
                println("  outcome: $outcome  <- the savepoint absorbed the unique violation")
                val stillReadable = Emails.selectAll().count()
                println("  a plain read AFTER the failed insert, same transaction, did not throw: $stillReadable rows")
                Verdict.Commit(outcome)
            }
            println("  committed rows: ${committedRows()}")

            println()
            println("=== a colliding insert, then a SECOND, unrelated insert in the same transaction")
            transactions.inTransaction {
                val first = insertGuarded("first@example.com")
                println("  first insert outcome: $first")
                val second = insertGuarded("second@example.com")
                println("  second insert outcome: $second  <- proves the transaction was not poisoned")
                Verdict.Commit(Unit)
            }
            println("  committed rows: ${committedRows()}")
        }
        println()
        println("Pool closed. Two rows should remain: first@example.com and second@example.com.")
    }
