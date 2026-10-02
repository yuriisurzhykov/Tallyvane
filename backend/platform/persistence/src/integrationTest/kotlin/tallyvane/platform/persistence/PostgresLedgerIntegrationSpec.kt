package tallyvane.platform.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import tallyvane.platform.idempotency.ClockSteppable
import tallyvane.platform.idempotency.Ledger
import tallyvane.platform.idempotency.LedgerConformance
import tallyvane.platform.kernel.TransactionRunner
import java.sql.DriverManager
import kotlin.time.Duration
import kotlin.time.Instant

private object Works : Table("works") {
    val n = integer("n")
}

/**
 * A write whose fate is a row count, counted over a connection of its own so the observation cannot be
 * fooled by the machinery it observes, and over committed rows only.
 */
private class WorksSubject(
    private val access: DatabaseAccess,
    private val clock: ClockSteppable,
    override val ledger: Ledger,
    override val transactions: TransactionRunner,
) : LedgerConformance.Subject {
    override suspend fun work() {
        Works.insert { it[n] = 1 }
    }

    override suspend fun survivingWork(): Int =
        DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from works").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    override fun later(by: Duration) = clock.advance(by)
}

/**
 * The Postgres ledger, judged by the same suite as the fake.
 *
 * Every case was already green on `LedgerFake`, so anything that fails here is a real disagreement
 * between the double the other modules test against and the code that runs. A database per case, and a
 * pool closed after it, for the reasons `ExposedTransactionRunnerSpec` gives.
 */
class PostgresLedgerIntegrationSpec : LedgerConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { persistence -> persistence.close() }
            opened.clear()
        }
    }

    override suspend fun fresh(): Subject {
        val access = PostgresFixture.migrated()
        DriverManager.getConnection(access.url, access.user, access.password.revealed()).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create table works (n integer not null)")
            }
        }
        val clock = ClockSteppable(Instant.parse("2026-10-01T12:00:00Z"))
        val persistence = PostgresPersistence(access, clock = clock).also { opened += it }
        return WorksSubject(access, clock, persistence.ledger, persistence.transactions)
    }
}
