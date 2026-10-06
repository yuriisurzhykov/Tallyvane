package tallyvane.journal.application

import tallyvane.journal.application.port.Entries
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.TransactionRunnerFake

/**
 * The fake, held to the suite the adapter over Postgres passes.
 */
class EntriesFakeSpec : EntriesConformance() {
    override suspend fun fresh(): Subject = object : Subject {
        override val entries: Entries = EntriesFake()
        override val transactions: TransactionRunner = TransactionRunnerFake()
    }
}
