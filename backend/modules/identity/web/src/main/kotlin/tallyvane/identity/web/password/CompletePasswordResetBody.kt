package tallyvane.identity.web.password

import kotlinx.serialization.Serializable

@Serializable
internal data class CompletePasswordResetBody(
    val challengeId: String,
    val email: String,
    val code: String,
    val newPassword: String,
)
