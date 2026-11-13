package tallyvane.platform.llm

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict

/**
 * [Memo] over `platform.model_memo`, each call in a transaction of its own.
 */
internal class PostgresMemo(private val transactions: TransactionRunner) : Memo {
    override suspend fun recall(key: String): String? = transactions.inTransaction {
        Verdict.Commit(
            ModelMemoTable.selectAll().where { ModelMemoTable.key eq key }.singleOrNull()?.get(ModelMemoTable.reply),
        )
    }

    override suspend fun keep(key: String, spec: String, model: String, text: String, tokens: TokenCount) {
        transactions.inTransaction {
            ModelMemoTable.insertIgnore {
                it[ModelMemoTable.key] = key
                it[ModelMemoTable.spec] = spec
                it[ModelMemoTable.model] = model
                it[reply] = text
                it[tokensIn] = tokens.input
                it[tokensOut] = tokens.output
                it[tokensCached] = tokens.cached
            }
            Verdict.Commit(Unit)
        }
    }

    override fun toString(): String = "PostgresMemo"
}
