package tallyvane.identity.application.email

import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret
import kotlin.uuid.Uuid

public data class ResetPasswordRequest(
    public val challengeId: Uuid,
    public val email: Email,
    public val code: Secret,
    public val newPassword: Secret,
)
