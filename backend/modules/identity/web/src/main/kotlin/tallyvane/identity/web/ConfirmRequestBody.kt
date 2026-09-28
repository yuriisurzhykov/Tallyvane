package tallyvane.identity.web

import kotlinx.serialization.Serializable

@Serializable
internal data class ConfirmRequestBody(val kind: String, val code: String)
