package tallyvane.identity.infrastructure.persistence

import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.SessionStoreConformance
import tallyvane.identity.application.port.UserRepository
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * [SessionStoreOverExposed] against a real Postgres, judged by the same suite as the fake
 * (ADR-046).
 */
class SessionStoreOverExposedSpec : SessionStoreConformance() {
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
            override val users: UserRepository = UserRepositoryOverExposed()
            override val sessions: SessionStore = SessionStoreOverExposed()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
