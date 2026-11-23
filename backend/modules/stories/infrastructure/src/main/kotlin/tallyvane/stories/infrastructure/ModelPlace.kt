package tallyvane.stories.infrastructure

import kotlinx.serialization.Serializable

@Serializable
internal class ModelPlace(
    val company: String? = null,
    val position: String? = null,
    val started: String? = null,
    val ended: String? = null,
    val title: String? = null,
)
