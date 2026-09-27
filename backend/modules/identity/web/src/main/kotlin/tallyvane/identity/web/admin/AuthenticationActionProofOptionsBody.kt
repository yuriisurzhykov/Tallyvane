package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationActionProofOptionsBody(val schemes: List<AuthenticationActionProofSchemeBody>)
