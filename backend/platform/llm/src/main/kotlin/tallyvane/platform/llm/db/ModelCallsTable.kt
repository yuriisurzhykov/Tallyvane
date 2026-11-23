package tallyvane.platform.llm.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal object ModelCallsTable : Table("platform.model_calls") {
    val id = long("id").autoIncrement()
    val at = timestamp("at")
    val accountId = uuid("account_id")
    val spec = text("spec")
    val providerId = uuid("provider_id")
    val model = text("model")
    val tokensIn = long("tokens_in")
    val tokensOut = long("tokens_out")
    val tokensCached = long("tokens_cached")
    val cost = double("cost_usd")
    val remembered = bool("remembered")
    override val primaryKey = PrimaryKey(id)
}
