package tallyvane.identity.web

import tallyvane.platform.http.FieldError
import tallyvane.platform.kernel.Failure

/**
 * One or more request fields that failed a domain value type's own `require` check — `Email`,
 * `Secret`, `DeviceLabel` and the rest all validate their own shape at construction, per those
 * types' own KDoc, rather than trusting whichever port handed the raw string over. This is where
 * that validation meets the wire: [FieldValidation] catches the resulting
 * `IllegalArgumentException` per field instead of letting it become an unhandled 500.
 */
internal data class RequestValidationFailure(val errors: List<FieldError>) : Failure
