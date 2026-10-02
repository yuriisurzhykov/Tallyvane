package tallyvane.identity.application

import tallyvane.identity.application.port.KeptAccounts
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake, held to the suite the adapter over Postgres passes.
 */
class KeptAccountsFakeSpec : KeptAccountsConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val accounts: KeptAccounts = KeptAccountsFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
