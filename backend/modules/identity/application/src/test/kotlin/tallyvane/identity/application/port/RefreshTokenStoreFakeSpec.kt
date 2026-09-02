package tallyvane.identity.application.port

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake

class RefreshTokenStoreFakeSpec : RefreshTokenStoreConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val users: UserRepository = UserRepositoryFake()
        override val sessions: SessionStore = SessionStoreFake()
        override val refreshTokens: RefreshTokenStore = RefreshTokenStoreFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
