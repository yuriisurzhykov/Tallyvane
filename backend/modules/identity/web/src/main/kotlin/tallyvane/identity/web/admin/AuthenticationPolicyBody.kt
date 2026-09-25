package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationPolicyBody(
    val version: Long,
    val schemes: List<AuthenticationSchemeBody>,
    val advancedAcknowledged: Boolean,
    val rules: List<AuthenticationPolicyRuleBody> = emptyList(),
)
