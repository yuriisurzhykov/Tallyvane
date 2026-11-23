package tallyvane.models.web

import kotlinx.serialization.Serializable

@Serializable
internal class ProviderShown(
    val id: String,
    val name: String,
    val kind: String,
    val baseUrl: String?,
    val keyHint: String,
    val fast: String,
    val careful: String,
    val priceIn: Double,
    val priceOut: Double,
    val active: Boolean,
)
