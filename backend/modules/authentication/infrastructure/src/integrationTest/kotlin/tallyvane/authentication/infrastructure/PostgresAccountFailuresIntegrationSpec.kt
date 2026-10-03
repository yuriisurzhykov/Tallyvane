package tallyvane.authentication.infrastructure

import tallyvane.authentication.application.AccountFailuresConformance
import tallyvane.authentication.application.port.AccountFailures
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046).
 */
class PostgresAccountFailuresIntegrationSpec : AccountFailuresConformance() {
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
            override val failures: AccountFailures = storageForTests().accountFailures()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
