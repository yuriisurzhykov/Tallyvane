package tallyvane.identity.web.mfa

import kotlinx.serialization.Serializable

@Serializable
internal data class EmailMfaRequestBody(val pendingId: String)

@Serializable
internal data class EmailMfaEnrollmentBody(val currentPassword: String)

@Serializable
internal data class EmailMfaConfirmBody(val challengeId: String, val code: String)
