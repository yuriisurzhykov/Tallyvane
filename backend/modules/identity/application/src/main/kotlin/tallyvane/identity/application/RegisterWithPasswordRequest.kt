package tallyvane.identity.application

import tallyvane.identity.domain.Email
import tallyvane.platform.kernel.Secret

public data class RegisterWithPasswordRequest(
    public val email: Email,
    public val rawPassword: Secret,
    public val displayName: String?,
)
