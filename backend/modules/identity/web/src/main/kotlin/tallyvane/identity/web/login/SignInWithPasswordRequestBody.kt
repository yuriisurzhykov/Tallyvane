package tallyvane.identity.web.login

import kotlinx.serialization.Serializable

@Serializable
internal data class SignInWithPasswordRequestBody(val email: String, val password: String, val device: String)
