package tallyvane.playground.pooloccupancy

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresPersistence
import java.sql.DriverManager

private val url = System.getProperty("spike.url", "jdbc:postgresql://localhost:5440/demo")
private val dbUser = System.getProperty("spike.user", "demo")
private val dbPassword = System.getProperty("spike.password", "demo")

/** Production pool size, as `PostgresPersistence` hardcodes it. */
private const val POOL_SIZE = 8

private const val SETTLE_MILLIS = 2_000L

/** Cases in `ExposedTransactionRunnerSpec`, each of which builds one `PostgresPersistence`. */
private const val SPEC_CASES = 7

/**
 * Backend connections, counted over a connection of its own so the observation cannot be
 * fooled by the pool it is observing. Excludes this observer and the postgres background
 * workers, which have no `datname`.
 */
private fun backends(): Int =
    DriverManager.getConnection(url, dbUser, dbPassword).use { connection ->
        connection.createStatement().use { statement ->
            statement
                .executeQuery(
                    "select count(*) from pg_stat_activity " +
                        "where datname is not null and pid <> pg_backend_pid()",
                ).use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
        }
    }

private fun limit(): String =
    DriverManager.getConnection(url, dbUser, dbPassword).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("show max_connections").use { rows ->
                rows.next()
                rows.getString(1)
            }
        }
    }

private fun report(what: String) {
    println("  %-52s %d".format(what, backends()))
}

/** The limit, or a note that asking for it needs a connection the server no longer has. */
private fun limitOrUnknown(): String = try {
    "max_connections=${limit()}"
} catch (unreachable: Exception) {
    "the limit (unreadable now: ${unreachable::class.simpleName})"
}

private fun pool(named: String, size: Int, idle: Int?): HikariDataSource {
    val configuration =
        HikariConfig().apply {
            poolName = named
            jdbcUrl = url
            username = dbUser
            password = dbPassword
            maximumPoolSize = size
            idle?.let { minimumIdle = it }
        }
    return HikariDataSource(configuration)
}

fun main() {
    println("Connecting to $url as $dbUser")
    println("max_connections on this server: ${limit()}")
    println("PostgresPersistence pool size:  $POOL_SIZE")
    println()

    println("=== one PostgresPersistence, the way the application will own it")
    report("before anything is built:")
    PostgresPersistence(DatabaseAccess(url, dbUser, dbPassword)).use {
        report("straight after the constructor returns:")
        Thread.sleep(SETTLE_MILLIS)
        report("after ${SETTLE_MILLIS}ms of housekeeping, still no query:")
        runBlocking { it.transactions.inTransaction { Verdict.Commit(Unit) } }
        report("after one trivial transaction:")
    }
    Thread.sleep(SETTLE_MILLIS)
    report("after close():")

    println()
    println("=== two of them at once, which is what a rolling deploy looks like")
    PostgresPersistence(DatabaseAccess(url, dbUser, dbPassword)).use {
        PostgresPersistence(DatabaseAccess(url, dbUser, dbPassword)).use {
            Thread.sleep(SETTLE_MILLIS)
            report("two instances, idle:")
        }
    }

    println()
    println("=== adding them one at a time, which is what one conformance spec does today")
    val many = mutableListOf<PostgresPersistence>()
    try {
        repeat(SPEC_CASES) { case ->
            many += PostgresPersistence(DatabaseAccess(url, dbUser, dbPassword))
            Thread.sleep(SETTLE_MILLIS)
            report("instance ${case + 1} of $SPEC_CASES built, all idle:")
        }
    } catch (refused: Exception) {
        println("  with ${many.size} instances built, the server had no slot left:")
        println("    ${refused.message?.lineSequence()?.first()}")
        println("  ${many.size} x $POOL_SIZE exceeds ${limitOrUnknown()}, and the refusal fell on the counter's own")
        println("  connection - a spec holding this many pools cannot even be observed")
    }
    many.forEach { it.close() }

    println()
    println("=== the same pool size, but minimumIdle=1")
    pool("lazy", size = POOL_SIZE, idle = 1).use {
        Thread.sleep(SETTLE_MILLIS)
        report("idle, never asked for a connection:")
        it.connection.close()
        report("after borrowing one connection:")
    }

    println()
    println("=== a pool of one, which is all a sequential test needs")
    pool("single", size = 1, idle = null).use {
        Thread.sleep(SETTLE_MILLIS)
        report("idle:")
    }
    Thread.sleep(SETTLE_MILLIS)
    report("everything closed:")
}
