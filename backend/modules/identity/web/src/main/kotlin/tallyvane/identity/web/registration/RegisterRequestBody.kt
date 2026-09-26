package tallyvane.identity.web.registration

import kotlinx.serialization.Serializable

@Serializable
internal data class RegisterRequestBody(val email: String, val password: String, val displayName: String? = null)
