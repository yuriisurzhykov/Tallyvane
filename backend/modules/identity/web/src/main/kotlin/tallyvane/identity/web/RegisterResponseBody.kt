package tallyvane.identity.web

import kotlinx.serialization.Serializable

@Serializable
internal data class RegisterResponseBody(val userId: String)
