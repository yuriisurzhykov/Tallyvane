package tallyvane.identity.application.secondfactor

import tallyvane.identity.domain.secondfactor.AuthenticationPolicy

public sealed interface AuthenticationPolicyResult {
    public data class Policy(public val value: AuthenticationPolicy) : AuthenticationPolicyResult
    public data object Forbidden : AuthenticationPolicyResult
    public data object Conflict : AuthenticationPolicyResult
    public data object Invalid : AuthenticationPolicyResult
}
