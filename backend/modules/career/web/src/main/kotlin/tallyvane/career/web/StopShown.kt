package tallyvane.career.web

import kotlinx.serialization.Serializable

@Serializable
internal class StopShown(
    val skill: String,
    val name: String,
    val importance: Double,
    val level: String,
    val evidence: Int,
)
