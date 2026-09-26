package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationActionProofBody(
    val action: String,
    val tokens: List<PresentedAuthenticationTokenBody>,
)

@Serializable
internal data class PresentedAuthenticationTokenBody(
    val kind: String,
    val value: String,
    val challengeId: String? = null,
    val codeVerifier: String? = null,
    val redirectUri: String? = null,
)

@Serializable
internal data class RequestActionProofEmailCodeBody(val action: String, val kind: String)
