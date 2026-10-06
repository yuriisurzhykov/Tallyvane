package tallyvane.journal.infrastructure

import tallyvane.journal.application.EntriesConformance
import tallyvane.journal.application.port.Entries
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046).
 */
class PostgresEntriesIntegrationSpec : EntriesConformance() {
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
            override val entries: Entries = JournalStorageFactory().entries()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
