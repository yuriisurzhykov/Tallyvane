package tallyvane.identity.web.login

import kotlinx.serialization.Serializable

@Serializable
internal data class SignInOptionsBody(val primaryMethods: List<String>)
