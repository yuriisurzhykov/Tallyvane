package tallyvane.identity.web

import kotlinx.serialization.Serializable

@Serializable
internal data class EnrollResponseBody(val otpauthUri: String)
