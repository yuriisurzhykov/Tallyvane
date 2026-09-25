package tallyvane.identity.web.registration

import kotlinx.serialization.Serializable

@Serializable
internal data class ResendRegistrationEmailBody(val email: String)
