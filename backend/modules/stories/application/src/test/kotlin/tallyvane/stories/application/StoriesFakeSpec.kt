package tallyvane.stories.application

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake
import tallyvane.stories.application.port.Stories
import tallyvane.stories.application.port.Utterances

class StoriesFakeSpec : StoriesConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val stories: Stories = StoriesFake()
        override val utterances: Utterances = UtterancesFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
