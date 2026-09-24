package tallyvane.identity.web.registration

import kotlinx.serialization.Serializable

@Serializable
internal data class RegisterResponseBody(val userId: String, val challengeId: String?)
