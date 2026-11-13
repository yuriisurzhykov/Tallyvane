package tallyvane.career.infrastructure

import tallyvane.career.application.GoalsConformance
import tallyvane.career.application.port.Goals
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

class PostgresGoalsIntegrationSpec : GoalsConformance() {
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
            override val goals: Goals = CareerStorageFactory().goals()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
