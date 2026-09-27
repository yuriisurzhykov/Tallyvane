package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class RequestActionProofEmailCodeBody(val action: String, val kind: String)
