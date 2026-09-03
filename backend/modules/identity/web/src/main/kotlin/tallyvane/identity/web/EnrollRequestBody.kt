package tallyvane.identity.web

import kotlinx.serialization.Serializable

@Serializable
internal data class EnrollRequestBody(val kind: String)
