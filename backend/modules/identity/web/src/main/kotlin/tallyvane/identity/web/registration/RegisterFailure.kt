package tallyvane.identity.web.registration

import tallyvane.platform.kernel.Failure

internal sealed interface RegisterFailure : Failure {
    data object EmailTaken : RegisterFailure
    data object InvalidPassword : RegisterFailure
}
