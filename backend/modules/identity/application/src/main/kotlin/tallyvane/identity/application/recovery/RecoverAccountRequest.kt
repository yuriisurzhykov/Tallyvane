package tallyvane.identity.application.recovery

import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret

public data class RecoverAccountRequest(
    public val email: Email,
    public val recoveryCode: Secret,
    public val newPassword: Secret,
    public val device: DeviceLabel,
)
