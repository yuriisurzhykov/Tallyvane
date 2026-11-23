package tallyvane.stories.web

import kotlinx.serialization.Serializable

@Serializable
internal class StoryShown(
    val id: String,
    val title: String?,
    val company: String?,
    val position: String?,
    val started: String?,
    val ended: String?,
    val state: String,
    val missing: String?,
    val facts: List<FactShown>,
)
