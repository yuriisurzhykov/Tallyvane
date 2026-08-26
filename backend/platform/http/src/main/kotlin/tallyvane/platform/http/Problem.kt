package tallyvane.platform.http

import kotlinx.serialization.Serializable

/**
 * One error answer, in the shape §11.6 fixed and RFC 9457 defines.
 *
 * ### Why the constructor is internal
 *
 * A module cannot make one of these directly, and that is the point. If it could, `type` would
 * become a free string and `status` a free number: two modules would answer the same kind of
 * failure with different URIs, a typo would ship a 4xx nobody handles, and the OpenAPI
 * specification of slice 14 would have nothing enumerable to describe.
 *
 * Instead the companion offers a closed set of HTTP *meanings*. A module picks one and supplies
 * what only it knows — which field, which code, what to say. So the platform owns the vocabulary
 * of the protocol and the module owns the vocabulary of its domain, which is also what
 * `platform-knows-no-business` requires: `invalid` and `forbidden` are HTTP, not jobs.
 *
 * ### What renders it
 *
 * Nothing here. `ProblemAnswers` — installed once by `app` — sets the status, the
 * `application/problem+json` content type, the trace header, and writes the log line. A route
 * responds with this value and does none of that, so none of it can be forgotten.
 *
 * @property type stable identifier of the *kind* of failure, and the field a client branches
 * on. It stays the same even when [title] or [detail] is reworded.
 * @property title the kind, in human words; identical for every occurrence of one [type].
 * @property status HTTP status, repeated in the body so the document is self-contained when
 * it is forwarded or logged.
 * @property detail this occurrence, in human words. Never a driver's message: whatever a
 * module puts here reaches the client verbatim.
 * @property errors per-field detail for a validation failure, empty otherwise.
 */
@Serializable
public class Problem internal constructor(
    public val type: String,
    public val title: String,
    public val status: Int,
    public val detail: String? = null,
    public val errors: List<FieldError> = emptyList(),
) {
    public companion object {
        /**
         * The request was understood and rejected: 422, with the fields that offended.
         */
        public fun invalid(vararg errors: FieldError): Problem = invalid(errors.toList())

        public fun invalid(errors: List<FieldError>, detail: String? = null): Problem = Problem(
            type = uri("validation-failed"),
            title = "Validation failed",
            status = UNPROCESSABLE,
            detail = detail,
            errors = errors,
        )

        /**
         * The caller is known and may not do this: 403. Not 404 — hiding existence is a
         * decision for the route that knows whether leaking it matters.
         */
        public fun forbidden(detail: String? = null): Problem = Problem(
            type = uri("forbidden"),
            title = "Forbidden",
            status = FORBIDDEN,
            detail = detail,
        )

        /**
         * Nothing here to act on: 404.
         */
        public fun missing(detail: String? = null): Problem = Problem(
            type = uri("not-found"),
            title = "Not found",
            status = NOT_FOUND,
            detail = detail,
        )

        /**
         * The request conflicts with the current state: 409. A concurrent edit, a duplicate a
         * unique index refused, a state machine that has moved on.
         */
        public fun conflicting(detail: String? = null): Problem = Problem(
            type = uri("conflict"),
            title = "Conflict",
            status = CONFLICT,
            detail = detail,
        )

        /**
         * A dependency is unavailable and the request may be retried: 503.
         */
        public fun unavailable(detail: String? = null): Problem = Problem(
            type = uri("unavailable"),
            title = "Temporarily unavailable",
            status = UNAVAILABLE,
            detail = detail,
        )

        /**
         * Nobody predicted this, so it says nothing: 500 with no [detail] at all.
         *
         * The emptiness is the feature. This is what an escaped exception becomes, and an
         * exception's message carries hosts, ports, table names and occasionally credentials
         * (§17). There is no parameter here to leak them through.
         */
        public fun unexpected(): Problem = Problem(
            type = uri("internal"),
            title = "Internal error",
            status = INTERNAL,
        )

        private fun uri(kind: String): String = "$PREFIX$kind"

        private const val PREFIX = "https://tallyvane.com/errors/"

        private const val FORBIDDEN = 403

        private const val NOT_FOUND = 404

        private const val CONFLICT = 409

        private const val UNPROCESSABLE = 422

        private const val INTERNAL = 500

        private const val UNAVAILABLE = 503
    }
}
