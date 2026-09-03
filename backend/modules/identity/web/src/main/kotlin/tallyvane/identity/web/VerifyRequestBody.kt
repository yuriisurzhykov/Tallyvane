package tallyvane.identity.web

import kotlinx.serialization.Serializable

@Serializable
internal data class VerifyRequestBody(val pendingId: String, val kind: String, val code: String)
