package tallyvane.platform.llm

import org.jetbrains.exposed.v1.jdbc.insert
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.platform.llm.db.ModelCallsTable
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PostgresCallLog(private val transactions: TransactionRunner) : CallLog {
    override suspend fun record(
        at: Instant,
        account: Uuid,
        spec: String,
        provider: Uuid,
        model: String,
        tokens: TokenCount,
        cost: Double,
        remembered: Boolean,
    ) {
        transactions.inTransaction {
            ModelCallsTable.insert {
                it[ModelCallsTable.at] = at
                it[accountId] = account
                it[ModelCallsTable.spec] = spec
                it[providerId] = provider
                it[ModelCallsTable.model] = model
                it[tokensIn] = tokens.input
                it[tokensOut] = tokens.output
                it[tokensCached] = tokens.cached
                it[ModelCallsTable.cost] = cost
                it[ModelCallsTable.remembered] = remembered
            }
            Verdict.Commit(Unit)
        }
    }

    override fun toString(): String = "PostgresCallLog"
}
