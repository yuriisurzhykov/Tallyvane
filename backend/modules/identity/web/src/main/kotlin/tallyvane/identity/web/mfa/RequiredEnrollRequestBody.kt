package tallyvane.identity.web.mfa

import kotlinx.serialization.Serializable

@Serializable
internal data class RequiredEnrollRequestBody(val pendingId: String, val kind: String)
