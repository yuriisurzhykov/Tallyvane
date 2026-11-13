package tallyvane.platform.llm

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * [Providers] over Postgres, read in a bookkeeping transaction of its own.
 */
internal class PostgresProviders(private val records: ProviderRecords, private val transactions: TransactionRunner) :
    Providers {
    override suspend fun active(): Provider? = transactions.inTransaction { Verdict.Rollback(records.activeProvider()) }

    override fun toString(): String = "PostgresProviders"
}
