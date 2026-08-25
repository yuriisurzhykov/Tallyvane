package tallyvane.platform.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerConformance
import java.sql.DriverManager

private object Marks : Table("marks") {
    val n = integer("n")
}

/**
 * A write whose fate is a row count.
 *
 * The write goes through Exposed, because taking part in the ambient transaction is
 * the whole thing under test. The count does not: it opens its own JDBC connection,
 * so the observation cannot be fooled by the machinery it is observing, and it sees
 * committed rows only — which is what the suite asks for.
 */
private class MarksSubject(private val access: DatabaseAccess, override val transactions: TransactionRunner) :
    TransactionRunnerConformance.Subject {
    override suspend fun write() {
        Marks.insert { it[n] = 1 }
    }

    override suspend fun survivingWrites(): Int =
        DriverManager.getConnection(access.url, access.user, access.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from marks").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
}

/**
 * The Exposed adapter against a real Postgres, judged by the same suite as the fake.
 *
 * This is the moment ADR-046 pays for itself: every case here was already green on
 * `TransactionRunnerFake`, so anything that fails is a real disagreement between the
 * double every other module tests against and the code that actually runs.
 *
 * "Fresh" means an empty table, arranged over a plain JDBC connection rather than
 * through Exposed. A first draft did it with `suspendTransaction` and failed all seven
 * cases with "no default database found", because the arrangement ran before the pool
 * that would have registered one — a setup that depends on the code under test having
 * already worked.
 */
class ExposedTransactionRunnerSpec : TransactionRunnerConformance() {
    private val access = PostgresFixture.access()

    private val persistence by lazy { PostgresPersistence(access) }

    init {
        afterSpec { persistence.close() }
    }

    override suspend fun fresh(): Subject {
        DriverManager.getConnection(access.url, access.user, access.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create table if not exists marks (n integer not null)")
                statement.execute("truncate table marks")
            }
        }
        return MarksSubject(access, persistence.transactions)
    }
}
