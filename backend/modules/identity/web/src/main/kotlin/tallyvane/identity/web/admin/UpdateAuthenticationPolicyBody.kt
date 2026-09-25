package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class UpdateAuthenticationPolicyBody(
    val expectedVersion: Long,
    val rules: List<AuthenticationPolicyRuleBody>,
    val advancedAcknowledged: Boolean,
)
