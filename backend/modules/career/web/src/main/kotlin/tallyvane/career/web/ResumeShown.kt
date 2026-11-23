package tallyvane.career.web

import kotlinx.serialization.Serializable

@Serializable
internal class ResumeShown(val roles: List<RoleShown>, val skills: List<String>)
