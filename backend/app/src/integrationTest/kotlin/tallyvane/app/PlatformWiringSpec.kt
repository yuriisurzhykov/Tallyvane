package tallyvane.app

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.isActive
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import java.sql.DriverManager

private const val REQUESTED_POOL = 3

/**
 * How many times, and how long between, the connection count is polled: HikariCP fills to
 * `minimumIdle` on a housekeeping thread, so the number arrives shortly after first use rather than
 * during it.
 */
private const val ATTEMPTS = 50

private const val PAUSE_MILLIS = 100L

/**
 * The lifecycle of the objects the composition root owns.
 *
 * Every case here fails on the state of the code before slice 13: nothing closed the pool, nothing
 * cancelled the abandoned scope, and the pool size was a constant inside `PostgresPersistence`
 * rather than a setting the root supplies.
 */
class PlatformWiringSpec :
    StringSpec(
        {
            // B1. The debt from slice 8: `PostgresPersistence` is `AutoCloseable` and nobody
            // called it.
            "closing the platform closes the pool it opened" {
                val access = PostgresFixture.migrated()
                val platform = PlatformWiring(settings(access, port = 0))

                platform.persistence.transactions.inTransaction { Verdict.Commit(Unit) }
                platform.close()

                shouldThrowAny { platform.persistence.transactions.inTransaction { Verdict.Commit(Unit) } }
            }

            // B2. Work abandoned by a bounded health check lives in this scope; if shutdown leaves
            // it running, the process does not end when it was told to.
            "closing the platform cancels the scope abandoned work lives in" {
                val platform = PlatformWiring(settings(PostgresFixture.migrated(), port = 0))

                platform.abandoned.isActive shouldBe true
                platform.close()

                platform.abandoned.isActive shouldBe false
            }

            // B3. Observed on the server rather than by asking the pool, so the pool cannot satisfy
            // this by agreeing with itself. Fails while eight is a constant in the class.
            "opens as many connections as the configuration asked for, and no more" {
                val access = PostgresFixture.migrated()

                PlatformWiring(settings(access, port = 0, pool = REQUESTED_POOL)).use { platform ->
                    platform.persistence.transactions.inTransaction { Verdict.Commit(Unit) }

                    connectionsTo(access) shouldBe REQUESTED_POOL
                }
            }
        },
    )

/**
 * Backends attached to that database, counted from a connection of its own and excluding itself.
 *
 * Polled rather than read once: HikariCP fills up to `minimumIdle` on a housekeeping thread, so the
 * count arrives shortly after the first use rather than during it — measured in
 * `playground/pool-occupancy`, where eight appeared about two seconds in.
 */
private fun connectionsTo(access: DatabaseAccess): Int {
    val database = access.url.substringAfterLast('/')
    var seen = 0
    repeat(ATTEMPTS) {
        seen = DriverManager
            .getConnection(access.url, access.user, access.password.revealed())
            .use { connection ->
                connection
                    .prepareStatement(
                        "select count(*) from pg_stat_activity " +
                            "where datname = ? and pid <> pg_backend_pid()",
                    ).use { statement ->
                        statement.setString(1, database)
                        statement.executeQuery().use { rows ->
                            rows.next()
                            rows.getInt(1)
                        }
                    }
            }
        if (seen >= REQUESTED_POOL) {
            return seen
        }
        Thread.sleep(PAUSE_MILLIS)
    }
    return seen
}
