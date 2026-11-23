package tallyvane.models.web

import kotlinx.serialization.Serializable

@Serializable
internal class CostShown(
    val provider: String,
    val model: String,
    val spec: String,
    val calls: Long,
    val remembered: Long,
    val tokensIn: Long,
    val tokensOut: Long,
    val dollars: Double,
)
