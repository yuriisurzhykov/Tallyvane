package tallyvane.identity.web

import tallyvane.platform.kernel.Failure

internal sealed interface RegistrationEmailFailure : Failure {
    data object InvalidCode : RegistrationEmailFailure
}
