package tallyvane.identity.web

import kotlinx.serialization.Serializable

@Serializable
internal data class VerifyRegistrationEmailRequestBody(
    val userId: String,
    val challengeId: String,
    val email: String,
    val code: String,
)
