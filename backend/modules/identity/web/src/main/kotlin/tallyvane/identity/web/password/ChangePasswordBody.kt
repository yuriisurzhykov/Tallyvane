package tallyvane.identity.web.password

import kotlinx.serialization.Serializable

@Serializable
internal data class ChangePasswordBody(val newPassword: String)
