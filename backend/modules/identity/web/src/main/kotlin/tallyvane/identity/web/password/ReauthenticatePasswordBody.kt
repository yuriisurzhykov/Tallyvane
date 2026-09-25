package tallyvane.identity.web.password

import kotlinx.serialization.Serializable

@Serializable
internal data class ReauthenticatePasswordBody(val password: String)
