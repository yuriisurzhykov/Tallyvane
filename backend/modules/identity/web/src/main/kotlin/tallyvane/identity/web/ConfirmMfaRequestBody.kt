package tallyvane.identity.web

import kotlinx.serialization.Serializable

@Serializable
internal data class ConfirmMfaRequestBody(val kind: String, val code: String)
