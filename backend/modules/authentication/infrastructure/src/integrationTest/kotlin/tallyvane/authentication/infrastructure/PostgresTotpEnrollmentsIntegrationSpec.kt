package tallyvane.authentication.infrastructure

import tallyvane.authentication.application.SecondFactorStorage
import tallyvane.authentication.application.TotpEnrollmentsConformance
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * The adapters over Postgres, judged by the suite the fake already passes (ADR-046).
 *
 * Every case gets a database of its own, migrated from the module's own migrations, and the pool is
 * closed after each case.
 */
class PostgresTotpEnrollmentsIntegrationSpec : TotpEnrollmentsConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }
    }

    override suspend fun fresh(): SecondFactorStorage {
        val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }
        val storage = storageForTests()
        return object : SecondFactorStorage {
            override val enrollments: TotpEnrollments = storage.totpEnrollments()
            override val codes: RecoveryCodeSets = storage.recoveryCodeSets()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
