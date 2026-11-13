package tallyvane.platform.llm

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal object ModelProvidersTable : Table("platform.model_providers") {
    val id = uuid("id")
    val name = text("name")
    val kind = text("kind")
    val baseUrl = text("base_url").nullable()
    val sealedKey = text("sealed_key")
    val keyHint = text("key_hint")
    val fast = text("fast_model")
    val careful = text("careful_model")
    val priceIn = double("price_in")
    val priceOut = double("price_out")
    val active = bool("active")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}
