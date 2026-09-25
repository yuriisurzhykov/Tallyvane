package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthenticationPolicyBody(
    val version: Long,
    val rules: List<AuthenticationPolicyRuleBody>,
    val advancedAcknowledged: Boolean,
)
