package tallyvane.identity.application.port

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake

class PendingAuthenticationStoreFakeSpec : PendingAuthenticationStoreConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val users: UserRepository = UserRepositoryFake()
        override val pendingAuthentications: PendingAuthenticationStore = PendingAuthenticationStoreFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
