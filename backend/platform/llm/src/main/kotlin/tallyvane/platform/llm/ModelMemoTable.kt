package tallyvane.platform.llm

import org.jetbrains.exposed.v1.core.Table

/**
 * `platform.model_memo`: one row for each question the model answered.
 */
internal object ModelMemoTable : Table("platform.model_memo") {
    val key = text("key")
    val spec = text("spec")
    val model = text("model")
    val reply = text("reply")
    val tokensIn = long("tokens_in")
    val tokensOut = long("tokens_out")
    val tokensCached = long("tokens_cached")

    override val primaryKey = PrimaryKey(key)
}
