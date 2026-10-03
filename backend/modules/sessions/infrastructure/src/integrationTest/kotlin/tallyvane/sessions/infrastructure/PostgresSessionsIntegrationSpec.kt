package tallyvane.sessions.infrastructure

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence
import tallyvane.sessions.application.SessionsConformance
import tallyvane.sessions.application.port.Sessions

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046).
 */
class PostgresSessionsIntegrationSpec : SessionsConformance() {
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
            override val sessions: Sessions = SessionsStorageFactory().sessions()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
