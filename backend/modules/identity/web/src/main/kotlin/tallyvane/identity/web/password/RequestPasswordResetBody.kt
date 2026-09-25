package tallyvane.identity.web.password

import kotlinx.serialization.Serializable

@Serializable
internal data class RequestPasswordResetBody(val email: String)
