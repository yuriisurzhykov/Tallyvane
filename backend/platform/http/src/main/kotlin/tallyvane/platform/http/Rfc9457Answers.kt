package tallyvane.platform.http

/**
 * The [Answers] the renderer hands out, and the only implementation there is.
 *
 * `internal`, and that visibility is the whole guarantee rather than tidiness: if a module could
 * name this type it could construct one, and with one in hand it could build a [Problem] anywhere
 * — which is exactly the hole the receiver design closes. `Api` holds the single instance and
 * passes it only into [Problems.of] and [FailureTranslator.translate].
 *
 * It could not be nested inside [Answers]: Kotlin has no `internal` members in an interface, so
 * nesting would have made it public and given every module a constructor. Found by the compiler,
 * which is the right place to find it.
 */
internal class Rfc9457Answers : Answers {
    override fun malformed(detail: String?): Problem = Problem(
        type = uri("malformed-request"),
        title = "Malformed request",
        status = MALFORMED,
        detail = detail,
    )

    override fun invalid(errors: List<FieldError>, detail: String?): Problem = Problem(
        type = uri("validation-failed"),
        title = "Validation failed",
        status = UNPROCESSABLE,
        detail = detail,
        errors = errors,
    )

    override fun forbidden(detail: String?): Problem = Problem(
        type = uri("forbidden"),
        title = "Forbidden",
        status = FORBIDDEN,
        detail = detail,
    )

    override fun missing(detail: String?): Problem = Problem(
        type = uri("not-found"),
        title = "Not found",
        status = NOT_FOUND,
        detail = detail,
    )

    override fun conflicting(detail: String?): Problem = Problem(
        type = uri("conflict"),
        title = "Conflict",
        status = CONFLICT,
        detail = detail,
    )

    override fun unavailable(detail: String?): Problem = Problem(
        type = uri("unavailable"),
        title = "Temporarily unavailable",
        status = UNAVAILABLE,
        detail = detail,
    )

    override fun unexpected(): Problem = Problem(
        type = uri("internal"),
        title = "Internal error",
        status = INTERNAL,
    )

    private fun uri(kind: String): String = "$PREFIX$kind"

    private companion object {
        const val PREFIX = "https://tallyvane.com/errors/"

        const val MALFORMED = 400

        const val FORBIDDEN = 403

        const val NOT_FOUND = 404

        const val CONFLICT = 409

        const val UNPROCESSABLE = 422

        const val INTERNAL = 500

        const val UNAVAILABLE = 503
    }
}
