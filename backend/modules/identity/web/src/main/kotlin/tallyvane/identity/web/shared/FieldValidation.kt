package tallyvane.identity.web.shared

import tallyvane.platform.http.FieldError

/**
 * Turns a domain value type's own `require` check into a [FieldError] instead of an unhandled
 * exception — one instance per request, since [errorsOrNull] has to see every field a handler
 * tried before answering, not just the first one that failed.
 *
 * ```
 * val validation = FieldValidation.Accumulator()
 * val email = validation.field("email") { Email(body.email) }
 * val password = validation.field("password") { Secret(body.password) }
 * validation.errorsOrNull()?.let { return call.respond(Refused(RequestValidationFailure(it), problems)) }
 * // email and password are both non-null past this point
 * ```
 */
internal interface FieldValidation {
    fun <T> field(name: String, block: () -> T): T?
    fun errorsOrNull(): List<FieldError>?

    class Accumulator : FieldValidation {
        private val errors = mutableListOf<FieldError>()

        /**
         * @return [block]'s result, or `null` if it threw [IllegalArgumentException] — the exact
         * exception every domain value type's own `require` raises. [name] is the *client's* own
         * field name, exactly as [FieldError.field] requires.
         */
        override fun <T> field(name: String, block: () -> T): T? = try {
            block()
        } catch (cause: IllegalArgumentException) {
            errors += FieldError(name, cause.message ?: CODE)
            null
        }

        override fun errorsOrNull(): List<FieldError>? = errors.toList().takeIf { it.isNotEmpty() }

        private companion object {
            const val CODE = "invalid"
        }
    }
}
