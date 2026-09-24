package tallyvane.identity.web.shared

import tallyvane.platform.http.FieldError
import tallyvane.platform.kernel.Failure

internal sealed interface RequestValidationFailure : Failure {
    data class FieldsInvalid(val errors: List<FieldError>) : RequestValidationFailure
}
