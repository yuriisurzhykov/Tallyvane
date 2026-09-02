package tallyvane.identity.application.password

import tallyvane.identity.domain.user.Email
import tallyvane.platform.kernel.Secret

public data class SignInWithPasswordRequest(public val email: Email, public val rawPassword: Secret)
