package tallyvane.identity.application.port

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake

class TotpEnrollmentStoreFakeSpec : TotpEnrollmentStoreConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val users: UserRepository = UserRepositoryFake()
        override val enrollments: TotpEnrollmentStore = TotpEnrollmentStoreFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
