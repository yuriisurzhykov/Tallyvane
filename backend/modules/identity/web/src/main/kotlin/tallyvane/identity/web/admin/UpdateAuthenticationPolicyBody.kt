package tallyvane.identity.web.admin

import kotlinx.serialization.Serializable

@Serializable
internal data class UpdateAuthenticationPolicyBody(
    val expectedVersion: Long,
    val schemes: List<AuthenticationSchemeBody> = emptyList(),
    val advancedAcknowledged: Boolean,
    val rules: List<AuthenticationPolicyRuleBody> = emptyList(),
)
