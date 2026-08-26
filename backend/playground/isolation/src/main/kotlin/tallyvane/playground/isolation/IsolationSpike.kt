package tallyvane.playground.isolation

import tallyvane.platform.persistence.DatabaseAccess
import java.sql.Connection
import java.sql.SQLException

private val access =
    DatabaseAccess(
        url = System.getProperty("spike.url", "jdbc:postgresql://localhost:5436/demo"),
        user = System.getProperty("spike.user", "demo"),
        password = System.getProperty("spike.password", "demo"),
    )

private fun connect(isolation: Int = Connection.TRANSACTION_READ_COMMITTED): Connection =
    java.sql.DriverManager.getConnection(access.url, access.user, access.password).apply {
        autoCommit = false
        transactionIsolation = isolation
    }

private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

private fun Connection.count(sql: String): Int =
    createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }

private fun setUp(ddl: List<String>) {
    connect().use { connection ->
        ddl.forEach { connection.exec(it) }
        connection.commit()
    }
}

private fun report(step: String, outcome: String) = println("  $step: $outcome")

/**
 * Two sessions both check that an address is free, then both insert it.
 */
private fun checkThenInsert() {
    println()
    println("=== READ COMMITTED, no unique constraint: check then insert")
    setUp(listOf("drop table if exists people", "create table people (email text not null)"))

    val first = connect()
    val second = connect()
    first.use { a ->
        second.use { b ->
            report("A sees rows for ivan@x", a.count("select count(*) from people where email = 'ivan@x'").toString())
            report("B sees rows for ivan@x", b.count("select count(*) from people where email = 'ivan@x'").toString())
            a.exec("insert into people values ('ivan@x')")
            b.exec("insert into people values ('ivan@x')")
            a.commit()
            b.commit()
        }
    }
    connect().use { checker ->
        report("rows afterwards", checker.count("select count(*) from people").toString() + "  <- both got in")
    }
}

/**
 * The same race, with the constraint the database can actually enforce.
 *
 * Two facts, and the first is the one that surprises: while A holds an uncommitted insert of
 * the same value, B does not get a duplicate-key error — it *waits*, because uniqueness
 * cannot be decided until A's fate is known. A `statement_timeout` is what turns that wait
 * into an answer. The first version of this spike had no timeout and hung: B waited for A,
 * and A was committed by a line that came after B's blocked call.
 */
private fun uniqueConstraint() {
    println()
    println("=== READ COMMITTED, unique index: the same race")
    setUp(
        listOf(
            "drop table if exists people",
            "create table people (email text not null)",
            "create unique index people_email on people (email)",
        ),
    )

    val first = connect()
    val second = connect()
    first.use { a ->
        second.use { b ->
            a.exec("insert into people values ('ivan@x')")
            b.exec("set statement_timeout = '700ms'")
            report(
                "B while A is still open",
                describe(runCatching { b.exec("insert into people values ('ivan@x')") }.exceptionOrNull()),
            )
            b.rollback()
            a.commit()
            report(
                "B after A committed",
                describe(runCatching { b.exec("insert into people values ('ivan@x')") }.exceptionOrNull()),
            )
            b.rollback()
        }
    }
    connect().use { checker ->
        report("rows afterwards", checker.count("select count(*) from people").toString() + "  <- only one got in")
    }
}

/**
 * Two SERIALIZABLE transactions each read the whole table and write a row derived from it.
 */
private fun serializable() {
    println()
    println("=== SERIALIZABLE: both read the sum, both write from it")
    setUp(listOf("drop table if exists counters", "create table counters (n integer not null)"))

    val first = connect(Connection.TRANSACTION_SERIALIZABLE)
    val second = connect(Connection.TRANSACTION_SERIALIZABLE)
    var failed = false
    first.use { a ->
        second.use { b ->
            val fromA = a.count("select coalesce(sum(n), 0) from counters")
            val fromB = b.count("select coalesce(sum(n), 0) from counters")
            report("A read", fromA.toString())
            report("B read", fromB.toString())
            a.exec("insert into counters values (${fromA + 1})")
            b.exec("insert into counters values (${fromB + 1})")
            runCatching { a.commit() }.exceptionOrNull()?.let { report("A commit", describe(it)) }
            val second = runCatching { b.commit() }.exceptionOrNull()
            report("B commit", describe(second))
            failed = second != null
        }
    }
    if (failed) {
        report("retry of the failed one", retry())
    }
    connect().use { checker ->
        report("rows afterwards", checker.count("select count(*) from counters").toString())
    }
}

/**
 * What a caller must do with a serialization failure: run the whole thing again.
 */
private fun retry(): String =
    connect(Connection.TRANSACTION_SERIALIZABLE).use { connection ->
        val sum = connection.count("select coalesce(sum(n), 0) from counters")
        connection.exec("insert into counters values (${sum + 1})")
        connection.commit()
        "succeeded on the second attempt"
    }

private fun describe(cause: Throwable?): String =
    when (cause) {
        null            -> "committed"
        is SQLException -> "${cause::class.simpleName} sqlState=${cause.sqlState} ${cause.message?.take(70)}"
        else            -> "${cause::class.simpleName} ${cause.message?.take(70)}"
    }

fun main() {
    println("Connecting to ${access.url} as ${access.user}")
    checkThenInsert()
    uniqueConstraint()
    serializable()
}
