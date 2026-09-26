package tallyvane.identity.web.mfa

import kotlinx.serialization.Serializable

@Serializable
internal data class SecondFactorStatusBody(val enrolled: List<String>, val recentlyAuthenticated: Boolean)
