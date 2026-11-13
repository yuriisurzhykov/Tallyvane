package tallyvane.stories.infrastructure

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import tallyvane.stories.application.UtterancesConformance
import tallyvane.stories.application.port.Utterances

/**
 * The adapters over Postgres, judged by the suite the fakes already pass (ADR-046).
 */
class PostgresUtterancesIntegrationSpec : UtterancesConformance() {
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
            override val utterances: Utterances = StoriesFactory().utterances()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
