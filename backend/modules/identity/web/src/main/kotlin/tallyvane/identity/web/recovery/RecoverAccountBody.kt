package tallyvane.identity.web.recovery

import kotlinx.serialization.Serializable

@Serializable
internal data class RecoverAccountBody(
    val email: String,
    val recoveryCode: String,
    val newPassword: String,
    val device: String,
)
