package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class ResetMfaResponseBody(val status: String)
