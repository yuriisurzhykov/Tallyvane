package tallyvane.identity.application.port

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake

class CredentialRepositoryFakeSpec : CredentialRepositoryConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val users: UserRepository = UserRepositoryFake()
        override val credentials: CredentialRepository = CredentialRepositoryFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
