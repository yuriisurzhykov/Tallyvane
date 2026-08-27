package tallyvane.platform.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import tallyvane.platform.kernel.Verdict
import java.sql.Connection
import java.sql.DriverManager
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private object Handles : Table("handles") {
    val handle = text("handle")
}

private const val CLASH = "ivan@x"

/**
 * That the server bounds `PostgresPersistence` configures are actually in effect.
 *
 * This exists because two bounds in that class were not. They were passed as `Int` values, which
 * HikariCP stores and pgjdbc silently ignores, under a comment claiming they were what stopped a
 * hung connection — measured in `playground/timeout-bounds/`, found by looking rather than by any
 * test failing. Nothing here would have caught it either, so this is the test that would.
 *
 * ### What makes it able to fail for the reason it exists
 *
 * A conflicting uncommitted insert makes a second insert wait: uniqueness cannot be decided until
 * the first transaction's fate is known, so the waiter blocks with no error
 * (`playground/isolation/`). Waiting for a transaction to end is a lock wait, so `lock_timeout`
 * governs it — and the two outcomes are far enough apart to tell apart:
 *
 * - the bound arrived: `55P03`, in about the 3 s `lock_timeout` allows;
 * - the bound did not arrive: `57014` at around 10 s, when Exposed's own `defaultQueryTimeout`
 *   cancels instead.
 *
 * Different state, different duration. Asserting both is what stops this being a test that
 * merely observes a wait ending somehow. Verified by taking the bound away: the first case then
 * failed with `expected:<55P03> but was:<57014>`.
 *
 * The second case is not a guard on that bound — it passed with the bound removed, because
 * Exposed's cancel leaves the pool usable too. It guards the weaker property that a cancelled
 * statement is survivable at all, which `socketTimeout` firing instead would break.
 */
class PostgresPersistenceBoundsSpec :
    StringSpec(
        {
            "a statement blocked behind another transaction is cut off by lock_timeout, not by Exposed" {
                val access = PostgresFixture.empty()
                withTable(access)
                blocking(access).use { blocker ->
                    PostgresPersistence(access).use { persistence ->
                        val started = TimeSource.Monotonic.markNow()

                        val refusal =
                            shouldThrow<ExposedSQLException> {
                                persistence.transactions.inTransaction {
                                    Handles.insert { it[handle] = CLASH }
                                    Verdict.Commit(Unit)
                                }
                            }

                        val waited = started.elapsedNow()
                        refusal.cause?.let { it as java.sql.SQLException }?.sqlState shouldBe "55P03"
                        // Below Exposed's defaultQueryTimeout of 10s, which is what would end the
                        // wait if the server bound had never reached the connection.
                        waited shouldBeLessThan 8.seconds
                    }
                }
            }

            "the connection survives its statement being cancelled, unlike one killed by socketTimeout" {
                val access = PostgresFixture.empty()
                withTable(access)
                blocking(access).use { blocker ->
                    PostgresPersistence(access).use { persistence ->
                        shouldThrow<ExposedSQLException> {
                            persistence.transactions.inTransaction {
                                Handles.insert { it[handle] = CLASH }
                                Verdict.Commit(Unit)
                            }
                        }

                        blocker.rollback()

                        val written =
                            persistence.transactions.inTransaction {
                                Handles.insert { it[handle] = CLASH }
                                Verdict.Commit(1)
                            }
                        written shouldBe 1
                    }
                }
            }
        },
    )

private fun withTable(access: DatabaseAccess) =
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("create table handles (handle text not null)")
            statement.execute("create unique index handles_handle on handles (handle)")
        }
    }

/**
 * A transaction that has written [CLASH] and will not commit, so anything else writing it waits.
 */
private fun blocking(access: DatabaseAccess): Connection =
    DriverManager.getConnection(access.url, access.user, access.password.revealed()).apply {
        autoCommit = false
        createStatement().use { it.execute("insert into handles (handle) values ('$CLASH')") }
    }
