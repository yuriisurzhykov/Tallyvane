package tallyvane.identity.web.registration

import kotlinx.serialization.Serializable

@Serializable
internal data class ResendRegistrationEmailResponseBody(val challengeId: String?)
