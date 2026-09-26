package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationSchemeBody(
    val id: String,
    val action: String,
    val requiredTokens: List<String>,
    val assuranceRank: Int,
    val enabled: Boolean = true,
)
