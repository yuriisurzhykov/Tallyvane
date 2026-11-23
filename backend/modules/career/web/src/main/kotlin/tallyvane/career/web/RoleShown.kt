package tallyvane.career.web

import kotlinx.serialization.Serializable

@Serializable
internal class RoleShown(val company: String?, val position: String?, val period: String?, val bullets: List<String>)
