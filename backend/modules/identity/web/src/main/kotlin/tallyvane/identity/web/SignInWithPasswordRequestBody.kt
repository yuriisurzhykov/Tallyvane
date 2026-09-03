package tallyvane.identity.web

import kotlinx.serialization.Serializable

@Serializable
internal data class SignInWithPasswordRequestBody(val email: String, val password: String, val device: String)
