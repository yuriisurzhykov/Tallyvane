package tallyvane.stories.application

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.stories.application.port.Utterances

class UtterancesFakeSpec : UtterancesConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val utterances: Utterances = UtterancesFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
