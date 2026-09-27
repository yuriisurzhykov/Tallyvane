package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class PresentedAuthenticationTokenBody(
    val kind: String,
    val value: String,
    val challengeId: String? = null,
    val codeVerifier: String? = null,
    val redirectUri: String? = null,
)
