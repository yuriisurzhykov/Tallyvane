package tallyvane.platform.llm.db

import org.jetbrains.exposed.v1.core.Table

/**
 * `platform.model_spending`: what an account's calls of one spec cost on one day.
 */
internal object ModelSpendingTable : Table("platform.model_spending") {
    val accountId = uuid("account_id")
    val day = text("day")
    val spec = text("spec")
    val calls = integer("calls")
    val tokensIn = long("tokens_in")
    val tokensOut = long("tokens_out")
    val tokensCached = long("tokens_cached")

    override val primaryKey = PrimaryKey(accountId, day, spec)
}
