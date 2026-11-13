package tallyvane.career.web

import kotlinx.serialization.Serializable

@Serializable
internal class HomeShown(
    val step: String,
    val subject: String?,
    val detail: String?,
    val goal: String?,
    val goalName: String?,
    val route: List<StopShown>,
    val stories: Int,
    val roles: List<RoleChoice>,
)
