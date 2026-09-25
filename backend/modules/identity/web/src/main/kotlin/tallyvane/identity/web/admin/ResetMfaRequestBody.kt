package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class ResetMfaRequestBody(val email: String, val confirmation: Boolean)
