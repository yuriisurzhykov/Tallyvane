package tallyvane.playground.timeoutbounds

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.time.TimeSource

private object Widgets : Table("widgets") {
    val email = text("email")
}

private val url = System.getProperty("spike.url", "jdbc:postgresql://localhost:5438/demo")
private val dbUser = System.getProperty("spike.user", "demo")
private val dbPassword = System.getProperty("spike.password", "demo")

private const val CLASH = "ivan@x"

/** Every bound under test is set to this, so the numbers printed are comparable. */
private const val BOUND_SECONDS = 3

/** The spike's own bound. Nothing here may wait longer than this, whatever the driver does. */
private const val GIVE_UP_SECONDS = 8L

private fun raw(at: String = url): Connection = DriverManager.getConnection(at, dbUser, dbPassword)

private fun sql(statement: String) = raw().use { it.createStatement().use { s -> s.execute(statement) } }

private fun committed(): Int = raw().use { connection ->
    connection.createStatement().use { statement ->
        statement.executeQuery("select count(*) from widgets").use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }
}

/** The sql state is the part code branches on, so it is printed even when the message is long. */
private fun failure(cause: Throwable): String = when (cause) {
    is SQLException -> "${cause::class.simpleName} sqlState=${cause.sqlState} ${firstLine(cause)}"
    else -> "${cause::class.simpleName} ${cause.message}"
}

private fun firstLine(cause: Throwable): String =
    cause.message?.lineSequence()?.first()?.removePrefix("org.postgresql.util.PSQLException: ").orEmpty()

/**
 * Runs one attempt on a thread that may be abandoned.
 *
 * The two spikes written before this one both hung, for the same structural reason: they
 * asked how long something waits, using code that could itself wait forever. Here the
 * bound is outside the attempt, so "no bound fired" is a printable answer rather than a
 * hang.
 */
private fun attempt(what: String, block: () -> Unit) {
    val started = TimeSource.Monotonic.markNow()
    val done = CountDownLatch(1)
    var told = "returned normally"
    val worker = Thread {
        told = try {
            block()
            "returned normally"
        } catch (cause: Throwable) {
            failure(cause)
        }
        done.countDown()
    }
    worker.isDaemon = true
    worker.start()
    if (done.await(GIVE_UP_SECONDS, TimeUnit.SECONDS)) {
        println("  $what")
        println("      bound fired after ${started.elapsedNow().inWholeMilliseconds} ms: $told")
    } else {
        println("  $what")
        println("      STILL WAITING after ${GIVE_UP_SECONDS}s - no bound fired; abandoned")
    }
}

private fun pooled(named: String, socketTimeout: Any?, statementTimeout: Int?): HikariDataSource {
    val configuration = HikariConfig().apply {
        poolName = named
        jdbcUrl = statementTimeout?.let { "$url?options=-c statement_timeout=${it * 1000}" } ?: url
        username = dbUser
        password = dbPassword
        maximumPoolSize = 2
        connectionTimeout = 2_000
        socketTimeout?.let { addDataSourceProperty("socketTimeout", it) }
    }
    return HikariDataSource(configuration)
}

private fun exposed(over: DataSource, queryTimeout: Int?): Database = Database.connect(
    datasource = over,
    databaseConfig = DatabaseConfig {
        defaultMaxAttempts = 1
        queryTimeout?.let { defaultQueryTimeout = it }
    },
)

private fun insertRaw(connection: Connection) =
    connection.createStatement().use { it.execute("insert into widgets (email) values ('$CLASH')") }

private fun usable(pool: DataSource): String = try {
    pool.connection.use { connection ->
        connection.createStatement().use { it.executeQuery("select 1").use { rows -> rows.next() } }
    }
    "yes"
} catch (cause: Exception) {
    "no - ${failure(cause)}"
}

fun main() {
    // pgjdbc localises its messages, and a sql state next to an unreadable message is
    // half the evidence. The README quotes this output, so it is pinned to one language.
    java.util.Locale.setDefault(java.util.Locale.ENGLISH)
    println("Connecting to $url as $dbUser")
    sql("drop table if exists widgets")
    sql("create table widgets (email text not null)")
    sql("create unique index widgets_email on widgets (email)")

    // The blocker. Every attempt below waits on this, because uniqueness cannot be
    // decided until this transaction's fate is known - measured in playground/isolation.
    val blocker = raw()
    blocker.autoCommit = false
    insertRaw(blocker)
    println("A holds an uncommitted insert of '$CLASH'. Committed rows: ${committed()}")
    println("Every bound below is ${BOUND_SECONDS}s. The spike abandons anything still waiting at ${GIVE_UP_SECONDS}s.")

    println()
    println("=== pgjdbc socketTimeout, the bound PostgresPersistence relies on")
    attempt("socketTimeout=$BOUND_SECONDS in the JDBC url, plain DriverManager") {
        raw("$url?socketTimeout=$BOUND_SECONDS").use { insertRaw(it) }
    }
    pooled("int-property", socketTimeout = BOUND_SECONDS, statementTimeout = null).use { pool ->
        attempt("addDataSourceProperty(\"socketTimeout\", $BOUND_SECONDS) - an Int, as the production config passes it") {
            pool.connection.use { insertRaw(it) }
        }
    }
    pooled("string-property", socketTimeout = "$BOUND_SECONDS", statementTimeout = null).use { pool ->
        attempt("addDataSourceProperty(\"socketTimeout\", \"$BOUND_SECONDS\") - the same value as a String") {
            pool.connection.use { insertRaw(it) }
        }
    }

    println()
    println("=== why an Int is ignored: how a value survives the trip to the driver")
    val carried = java.util.Properties()
    carried.put("socketTimeout", BOUND_SECONDS)
    carried.put("alsoSocketTimeout", "$BOUND_SECONDS")
    println("  Properties.put(Int) then getProperty: ${carried.getProperty("socketTimeout")}")
    println("  Properties.put(String) then getProperty: ${carried.getProperty("alsoSocketTimeout")}")
    println("  both are present as entries: ${carried.keys.map { it.toString() }.sorted()}")

    println()
    println("=== pgjdbc connectTimeout, the other Int in the production config")
    val blackhole = "jdbc:postgresql://10.255.255.1:5432/demo"
    attempt("connectTimeout=$BOUND_SECONDS as an Int property, to an address that never answers") {
        HikariConfig().apply {
            poolName = "connect-int"
            jdbcUrl = blackhole
            username = dbUser
            password = dbPassword
            connectionTimeout = 60_000
            addDataSourceProperty("connectTimeout", BOUND_SECONDS)
        }.let { HikariDataSource(it) }.use { it.connection.close() }
    }
    attempt("connectTimeout=\"$BOUND_SECONDS\" as a String property, same address") {
        HikariConfig().apply {
            poolName = "connect-string"
            jdbcUrl = blackhole
            username = dbUser
            password = dbPassword
            connectionTimeout = 60_000
            addDataSourceProperty("connectTimeout", "$BOUND_SECONDS")
        }.let { HikariDataSource(it) }.use { it.connection.close() }
    }

    println()
    println("=== Exposed defaultQueryTimeout, the other bound PostgresPersistence sets")
    pooled("query-timeout", socketTimeout = null, statementTimeout = null).use { pool ->
        val database = exposed(pool, queryTimeout = BOUND_SECONDS)
        attempt("insert through Exposed") {
            transaction(database) { Widgets.insert { it[email] = CLASH } }
        }
        println("      pool still usable: ${usable(pool)}")
        attempt("the same insert as raw JDBC on the same pool, bypassing Exposed") {
            pool.connection.use { insertRaw(it) }
        }
    }

    println()
    println("=== server statement_timeout, set in the connection options")
    pooled("statement-timeout", socketTimeout = null, statementTimeout = BOUND_SECONDS).use { pool ->
        val database = exposed(pool, queryTimeout = null)
        attempt("insert through Exposed") {
            transaction(database) { Widgets.insert { it[email] = CLASH } }
        }
        println("      pool still usable: ${usable(pool)}")
        attempt("the same insert as raw JDBC on the same pool, bypassing Exposed") {
            pool.connection.use { insertRaw(it) }
        }
        println("      pool still usable: ${usable(pool)}")
    }

    blocker.rollback()
    blocker.close()
    Thread.sleep(1_000)
    println()
    println("Blocker rolled back. Committed rows: ${committed()} - anything abandoned above was still")
    println("queued at that moment, so it lands here: that is what an unbounded wait costs.")
}
