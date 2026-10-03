package tallyvane.authentication.infrastructure

import tallyvane.authentication.application.AttemptsConformance
import tallyvane.authentication.application.port.Attempts
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046).
 *
 * Every case gets a database of its own, migrated from the module's own migrations, and the pool is
 * closed after each case: a pool holds its connections from the moment it is built, and a spec that
 * kept one per case alive would exhaust the server.
 */
class PostgresAttemptsIntegrationSpec : AttemptsConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }
    }

    override suspend fun fresh(): Subject {
        val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }
        return object : Subject {
            override val attempts: Attempts = storageForTests().attempts()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
