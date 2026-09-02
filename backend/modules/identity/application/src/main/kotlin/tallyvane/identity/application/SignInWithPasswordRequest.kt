package tallyvane.identity.application

import tallyvane.identity.domain.Email
import tallyvane.platform.kernel.Secret

public data class SignInWithPasswordRequest(public val email: Email, public val rawPassword: Secret)
