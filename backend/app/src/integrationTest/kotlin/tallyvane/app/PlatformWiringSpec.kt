package tallyvane.app

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.isActive
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.platform.persistence.PostgresFixture
import java.sql.DriverManager

/**
 * Asked for and then observed on the server. Any number would do as long as the case can tell it
 * apart from a default, which is the whole point of the case.
 */
private const val REQUESTED_POOL = 3

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

                platform.persistence.transactions.touched()
                platform.close()

                shouldThrowAny { platform.persistence.transactions.touched() }
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
                    platform.persistence.transactions.touched()

                    connectionsTo(access) shouldBe REQUESTED_POOL
                }
            }
        },
    )

/**
 * One transaction that actually issues a statement, which is the only kind that needs the pool:
 * Exposed asks for a connection when a statement does, so an empty block would run happily against
 * a closed pool. See `app/README.md` for the case where that mattered.
 */
private suspend fun TransactionRunner.touched(): Boolean = inTransaction {
    Verdict.Commit(TransactionManager.current().exec("select 1") { rows -> rows.next() } ?: false)
}

/**
 * Backends attached to that database, counted from a connection of its own and excluding itself.
 *
 * Polled through [awaited], because HikariCP fills the pool on a housekeeping thread and the count
 * arrives shortly after the first use rather than during it.
 */
private fun connectionsTo(access: DatabaseAccess): Int = awaited(REQUESTED_POOL) {
    DriverManager
        .getConnection(access.url, access.user, access.password.revealed())
        .use { connection ->
            connection
                .prepareStatement(
                    "select count(*) from pg_stat_activity where datname = ? and pid <> pg_backend_pid()",
                ).use { statement ->
                    statement.setString(1, access.url.substringAfterLast('/'))
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getInt(1)
                    }
                }
        }
}
