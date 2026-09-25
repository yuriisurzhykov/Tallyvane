package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationPolicyRuleBody(
    val primary: String,
    val enabled: Boolean,
    val requirement: String,
    val allowedMethods: List<String>,
)
