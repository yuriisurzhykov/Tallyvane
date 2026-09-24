package tallyvane.identity.web.mfa

import kotlinx.serialization.Serializable

@Serializable
internal data class EmailMfaChallengeResponseBody(val challengeId: String)
