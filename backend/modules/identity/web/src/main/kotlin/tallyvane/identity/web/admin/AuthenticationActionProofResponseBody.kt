package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationActionProofResponseBody(
    val proof: String,
    val schemeId: String,
    val assuranceRank: Int,
    val expiresAt: String,
)
