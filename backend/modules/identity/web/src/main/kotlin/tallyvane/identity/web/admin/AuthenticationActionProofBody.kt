package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationActionProofBody(
    val action: String,
    val tokens: List<PresentedAuthenticationTokenBody>,
)
