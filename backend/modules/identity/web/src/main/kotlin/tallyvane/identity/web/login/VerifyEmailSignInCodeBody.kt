package tallyvane.identity.web.login

import kotlinx.serialization.Serializable

@Serializable
internal data class VerifyEmailSignInCodeBody(
    val challengeId: String,
    val email: String,
    val code: String,
    val device: String = "Browser",
)
