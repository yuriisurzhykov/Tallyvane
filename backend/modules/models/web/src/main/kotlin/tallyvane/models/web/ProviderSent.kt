package tallyvane.models.web

import kotlinx.serialization.Serializable

/**
 * A provider as the admin form sends it. An empty or absent key keeps the key already kept.
 */
@Serializable
internal class ProviderSent(
    val name: String,
    val kind: String,
    val baseUrl: String? = null,
    val key: String? = null,
    val fast: String,
    val careful: String,
    val priceIn: Double,
    val priceOut: Double,
)
