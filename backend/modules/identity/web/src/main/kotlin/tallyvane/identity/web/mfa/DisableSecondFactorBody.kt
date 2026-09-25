package tallyvane.identity.web.mfa

import kotlinx.serialization.Serializable

@Serializable
internal data class DisableSecondFactorBody(val kind: String, val confirmed: Boolean)
