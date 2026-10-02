package tallyvane.identity.infrastructure

import tallyvane.identity.application.KeptAccountsConformance
import tallyvane.identity.application.port.KeptAccounts
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.persistence.PostgresFixture
import tallyvane.platform.persistence.PostgresPersistence

/**
 * The adapter over Postgres, judged by the suite the fake already passes (ADR-046).
 */
class PostgresKeptAccountsIntegrationSpec : KeptAccountsConformance() {
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
            override val accounts: KeptAccounts = IdentityStorageFactory().accounts()
            override val transactions: TransactionRunner = persistence.transactions
        }
    }
}
