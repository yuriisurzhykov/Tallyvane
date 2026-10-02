package tallyvane.authentication.infrastructure

import tallyvane.authentication.application.GoogleProfilesConformance
import tallyvane.authentication.application.port.Attempts
import tallyvane.authentication.application.port.GoogleProfiles
import tallyvane.platform.kernel.IdGeneratorFake
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046).
 */
class PostgresGoogleProfilesIntegrationSpec : GoogleProfilesConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }
    }

    override suspend fun fresh(): Subject {
        val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }
        val storage = AuthenticationStorageFactory(IdGeneratorFake())
        return object : Subject {
            override val attempts: Attempts = storage.attempts()
            override val profiles: GoogleProfiles = storage.googleProfiles()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
