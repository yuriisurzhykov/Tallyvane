package tallyvane.platform.llm

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import kotlin.uuid.Uuid

/**
 * [Spending] over `platform.model_spending`, each call in a transaction of its own.
 */
internal class PostgresSpending(private val transactions: TransactionRunner) : Spending {
    override suspend fun spentOn(account: Uuid, day: String): Long = transactions.inTransaction {
        Verdict.Commit(
            ModelSpendingTable.selectAll()
                .where { (ModelSpendingTable.accountId eq account) and (ModelSpendingTable.day eq day) }
                .sumOf { row -> row[ModelSpendingTable.tokensIn] + row[ModelSpendingTable.tokensOut] },
        )
    }

    override suspend fun add(account: Uuid, day: String, spec: String, tokens: TokenCount) {
        transactions.inTransaction {
            ModelSpendingTable.upsert(
                onUpdate = {
                    it[ModelSpendingTable.calls] = ModelSpendingTable.calls + 1
                    it[ModelSpendingTable.tokensIn] = ModelSpendingTable.tokensIn + tokens.input
                    it[ModelSpendingTable.tokensOut] = ModelSpendingTable.tokensOut + tokens.output
                    it[ModelSpendingTable.tokensCached] = ModelSpendingTable.tokensCached + tokens.cached
                },
            ) {
                it[accountId] = account
                it[ModelSpendingTable.day] = day
                it[ModelSpendingTable.spec] = spec
                it[calls] = 1
                it[tokensIn] = tokens.input
                it[tokensOut] = tokens.output
                it[tokensCached] = tokens.cached
            }
            Verdict.Commit(Unit)
        }
    }

    override fun toString(): String = "PostgresSpending"
}
