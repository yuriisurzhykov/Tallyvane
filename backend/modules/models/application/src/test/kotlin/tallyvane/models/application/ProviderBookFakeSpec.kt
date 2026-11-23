package tallyvane.models.application

import tallyvane.models.application.port.ProviderBook
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake

class ProviderBookFakeSpec : ProviderBookConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val book: ProviderBook = ProviderBookFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
