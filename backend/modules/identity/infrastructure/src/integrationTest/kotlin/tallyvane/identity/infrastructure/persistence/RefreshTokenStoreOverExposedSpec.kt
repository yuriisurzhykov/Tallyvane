package tallyvane.identity.infrastructure.persistence

import tallyvane.identity.application.port.RefreshTokenStore
import tallyvane.identity.application.port.RefreshTokenStoreConformance
import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.UserRepository
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * [RefreshTokenStoreOverExposed] against a real Postgres, judged by the same suite as the fake
 * (ADR-046) — including [RefreshTokenStore.rotate]'s savepoint-free atomic conditional update,
 * which only a real database can actually exercise.
 */
class RefreshTokenStoreOverExposedSpec : RefreshTokenStoreConformance() {
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
            override val refreshTokens: RefreshTokenStore = RefreshTokenStoreOverExposed()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
