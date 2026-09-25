package tallyvane.identity.web.registration

import kotlinx.serialization.Serializable

@Serializable
internal data class VerifyRegistrationEmailRequestBody(
    val challengeId: String,
    val email: String,
    val code: String,
)
