package tallyvane.identity.web.shared

import kotlinx.serialization.Serializable

@Serializable
internal data class EmailChallengeResponseBody(val challengeId: String)
