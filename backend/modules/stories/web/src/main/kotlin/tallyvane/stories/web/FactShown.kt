package tallyvane.stories.web

import kotlinx.serialization.Serializable

@Serializable
internal class FactShown(
    val id: String,
    val kind: String,
    val text: String,
    val figures: List<Double>,
    val technologies: List<String>,
    val skills: List<String>,
    val mine: Boolean,
    val quotes: List<String>,
)
