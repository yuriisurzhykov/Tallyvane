package tallyvane.identity.web

import tallyvane.platform.kernel.Failure

internal sealed interface RegisterFailure : Failure {
    data object EmailTaken : RegisterFailure
}
