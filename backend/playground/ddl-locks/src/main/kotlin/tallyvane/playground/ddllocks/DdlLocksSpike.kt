package tallyvane.playground.ddllocks

import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Server-side timeouts in the URL, not in this code. Two earlier versions of this spike hung
// forever because the bound lived in statements my own blocked code was supposed to reach.
// A measurement of waiting must not be able to wait indefinitely.
private val url =
    System.getProperty(
        "spike.url",
        "jdbc:postgresql://localhost:5437/demo?options=-c%20statement_timeout%3D5000",
    )
private val user = System.getProperty("spike.user", "demo")
private val password = System.getProperty("spike.password", "demo")

private fun connect(): Connection = DriverManager.getConnection(url, user, password)

private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

private fun report(step: String, outcome: String) = println("  $step: $outcome")

private fun describe(cause: Throwable?): String =
    when (cause) {
        null -> "succeeded"
        is SQLException -> "sqlState=${cause.sqlState} ${cause.message?.lineSequence()?.first()?.take(90)}"
        else -> "${cause::class.simpleName} ${cause.message?.take(90)}"
    }

/**
 * Does Flyway let a migration create an index concurrently?
 *
 * `CREATE INDEX CONCURRENTLY` cannot run inside a transaction block, and Flyway wraps a
 * migration in one. The `.conf` sidecar beside the script is the documented way to opt out;
 * this measures both halves.
 */
private fun concurrentIndexUnderFlyway(withOptOut: Boolean) {
    val label = if (withOptOut) "with executeInTransaction=false" else "default (in a transaction)"
    println()
    println("=== Flyway: create index concurrently, $label")
    connect().use { it.exec("drop schema if exists spike cascade") }

    val flyway =
        Flyway
            .configure()
            .dataSource(url, user, password)
            .locations("classpath:db/spike")
            .schemas("spike")
            .defaultSchema("spike")
            .createSchemas(true)
            .let { if (withOptOut) it.configuration(mapOf("flyway.executeInTransaction" to "false")) else it }
            .load()

    report("migrate", describe(runCatching { flyway.migrate() }.exceptionOrNull()))
}

/**
 * What an ALTER TABLE does to readers while it waits for its own lock.
 */
private fun alterBehindALongReader(lockTimeout: String?) {
    val label = lockTimeout?.let { "with lock_timeout=$it" } ?: "without lock_timeout"
    println()
    println("=== ALTER TABLE behind an open reader, $label")
    connect().use { setup ->
        setup.exec("drop table if exists widgets cascade")
        setup.exec("create table widgets (id integer not null)")
        setup.exec("insert into widgets values (1)")
    }

    val reader = connect().apply { autoCommit = false }
    reader.createStatement().use { it.executeQuery("select count(*) from widgets").close() }

    val pool = Executors.newFixedThreadPool(2)
    val altering = CountDownLatch(1)
    val alter =
        pool.submit<String> {
            connect().use { connection ->
                lockTimeout?.let { connection.exec("set lock_timeout = '$it'") }
                connection.exec("set statement_timeout = '4s'")
                altering.countDown()
                describe(runCatching { connection.exec("alter table widgets add column label text") }.exceptionOrNull())
            }
        }
    altering.await()
    Thread.sleep(300)

    val laterReader =
        pool.submit<String> {
            connect().use { connection ->
                connection.exec("set statement_timeout = '2s'")
                describe(
                    runCatching {
                        connection.createStatement().use { it.executeQuery("select count(*) from widgets").close() }
                    }.exceptionOrNull(),
                )
            }
        }

    report("the ALTER", alter.get(20, TimeUnit.SECONDS))
    report("a plain SELECT arriving after it", laterReader.get(20, TimeUnit.SECONDS))
    reader.rollback()
    reader.close()
    pool.shutdownNow()
}

fun main() {
    println("Connecting to $url as $user")
    // The Flyway half is answered and left out of the default run: with a transaction it
    // refuses the migration outright, and without one `create index concurrently` waits on
    // Flyway's own connection, which sits idle in a transaction. Pass -Pspike.flyway=true to
    // watch it hang again on purpose.
    if (System.getProperty("spike.flyway") == "true") {
        concurrentIndexUnderFlyway(withOptOut = false)
        concurrentIndexUnderFlyway(withOptOut = true)
    }
    alterBehindALongReader(lockTimeout = null)
    alterBehindALongReader(lockTimeout = "300ms")
}
