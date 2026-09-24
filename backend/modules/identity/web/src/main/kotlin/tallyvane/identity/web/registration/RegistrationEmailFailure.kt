package tallyvane.identity.web.registration

import tallyvane.platform.kernel.Failure

internal sealed interface RegistrationEmailFailure : Failure {
    data object InvalidCode : RegistrationEmailFailure
}
