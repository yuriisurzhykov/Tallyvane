package tallyvane.identity.infrastructure.persistence

import tallyvane.identity.application.port.UserRepository
import tallyvane.identity.application.port.UserRepositoryConformance
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * [UserRepositoryOverExposed] against a real Postgres, judged by the same suite as the fake —
 * the moment ADR-046 pays for itself: every case already passed against
 * [tallyvane.identity.application.port.UserRepositoryFake], so anything that fails here is a real
 * disagreement between the double every use-case test runs against and the code that actually
 * runs in production.
 */
class UserRepositoryOverExposedSpec : UserRepositoryConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        // One pool per case, not per spec — a pool holds its full size in open connections from
        // the moment it is built, and closing only at the end of the spec would leave every
        // earlier case's pool alive at once. `ExposedTransactionRunnerSpec` in platform:persistence
        // states the same reasoning.
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }
    }

    override suspend fun fresh(): Subject {
        val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }
        return object : Subject {
            override val users: UserRepository = UserRepositoryOverExposed()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
