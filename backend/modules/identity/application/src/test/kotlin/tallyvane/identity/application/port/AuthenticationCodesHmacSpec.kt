package tallyvane.identity.application.port

import tallyvane.platform.kernel.Secret

class AuthenticationCodesHmacSpec : AuthenticationCodesConformance() {
    override fun fresh(): AuthenticationCodes = AuthenticationCodes.Hmac(Secret("test-only-code-pepper-32-bytes-long"))
}
