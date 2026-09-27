package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationActionProofSchemeBody(
    val id: String,
    val requiredTokens: List<String>,
    val assuranceRank: Int,
)
