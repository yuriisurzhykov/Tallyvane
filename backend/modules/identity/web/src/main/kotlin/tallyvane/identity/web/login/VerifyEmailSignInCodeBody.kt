package tallyvane.identity.web.login

internal data class VerifyEmailSignInCodeBody(
    val challengeId: String,
    val email: String,
    val code: String,
    val device: String = "Browser",
)
