package tallyvane.identity.web.mfa

import kotlinx.serialization.Serializable

@Serializable
internal data class RequiredConfirmRequestBody(val pendingId: String, val kind: String, val code: String)
