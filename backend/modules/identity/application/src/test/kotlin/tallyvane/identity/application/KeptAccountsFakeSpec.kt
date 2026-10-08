package tallyvane.identity.application

import tallyvane.identity.application.port.KeptAccounts
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.uuid.Uuid

/**
 * The fake, held to the suite the adapter over Postgres passes.
 */
class KeptAccountsFakeSpec : KeptAccountsConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        private val fake = KeptAccountsFake()
        override val accounts: KeptAccounts = fake
        override val transactions: TransactionRunner = TransactionRunnerFake()

        override suspend fun grantAdministrator(id: Uuid) = fake.grantAdministrator(id)
    }
}
