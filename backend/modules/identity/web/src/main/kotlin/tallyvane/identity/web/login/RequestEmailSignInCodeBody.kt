package tallyvane.identity.web.login

import kotlinx.serialization.Serializable

@Serializable
internal data class RequestEmailSignInCodeBody(val email: String)
