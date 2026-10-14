package tallyvane.authentication.infrastructure

import tallyvane.authentication.application.EnrollmentCensusConformance
import tallyvane.authentication.application.SecondFactorStorage
import tallyvane.authentication.application.port.EnrollmentCensus
import tallyvane.authentication.application.port.RecoveryCodeSets
import tallyvane.authentication.application.port.TotpEnrollments
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * The census over Postgres, judged by the suite the fake already passes (ADR-046).
 */
class PostgresEnrollmentCensusIntegrationSpec : EnrollmentCensusConformance() {
    private val opened = mutableListOf<PostgresPersistence>()

    init {
        afterTest {
            opened.forEach { it.close() }
            opened.clear()
        }
    }

    override suspend fun fresh(): Subject {
        val persistence = PostgresPersistence(PostgresFixture.migrated()).also { opened += it }
        val factory = storageForTests()
        return object : Subject {
            override val storage: SecondFactorStorage = object : SecondFactorStorage {
                override val enrollments: TotpEnrollments = factory.totpEnrollments()
                override val codes: RecoveryCodeSets = factory.recoveryCodeSets()
                override val transactions: TransactionRunner = persistence.transactions
            }
            override val census: EnrollmentCensus = factory.enrollmentCensus()
        }
    }
}
