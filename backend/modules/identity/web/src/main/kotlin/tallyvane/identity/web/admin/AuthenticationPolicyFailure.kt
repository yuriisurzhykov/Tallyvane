package tallyvane.identity.web.admin

import tallyvane.platform.kernel.Failure

internal sealed interface AuthenticationPolicyFailure : Failure {
    data object Forbidden : AuthenticationPolicyFailure
    data object Conflict : AuthenticationPolicyFailure
    data object Invalid : AuthenticationPolicyFailure
}
