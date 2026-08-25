package tallyvane.platform.kernel

import io.kotest.matchers.shouldBe

class TransactionRunnerFakeSpec : TransactionRunnerConformance() {
    override suspend fun fresh(): Subject {
        val fake = TransactionRunnerFake()
        return object : Subject {
            override val transactions: TransactionRunner = fake

            override suspend fun write() = fake.write()

            override suspend fun survivingWrites(): Int = fake.survivingWrites()
        }
    }

    init {
        "records how each transaction ended, so a use-case test can assert a refusal rolled back" {
            val fake = TransactionRunnerFake()

            fake.inTransaction { Verdict.Commit(Unit) }
            fake.inTransaction { Verdict.Rollback(Unit) }

            fake.endings shouldBe
                listOf(TransactionRunnerFake.Ending.Committed, TransactionRunnerFake.Ending.RolledBack)
        }
    }
}
