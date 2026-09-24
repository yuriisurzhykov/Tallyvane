package tallyvane.identity.web.password

internal data class CompletePasswordResetBody(
    val challengeId: String,
    val email: String,
    val code: String,
    val newPassword: String,
)
