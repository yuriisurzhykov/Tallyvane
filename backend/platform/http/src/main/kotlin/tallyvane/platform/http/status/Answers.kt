package tallyvane.platform.http.status

import tallyvane.platform.http.FieldError
import tallyvane.platform.http.problems.FailureTranslator
import tallyvane.platform.http.problems.Problem
import tallyvane.platform.http.problems.Problems

/**
 * The only source of a [Problem] a module can reach.
 *
 * Not the only source there is, and the distinction became load-bearing on 2026-08-26: [Statuses]
 * also makes one, for the statuses Ktor answers on its own. That port is `internal`, so no module can
 * name it — which is why this one can still promise a module twelve meanings and no way to invent a
 * thirteenth.
 *
 * ### Why a receiver instead of a companion
 *
 * `Problem.forbidden()` as a public factory was the first design, and it made the contract
 * breakable in one line: a route could answer with a problem it built itself, never touch its
 * module's [Problems] table, and nothing — no type, no rule — would notice. The mapping table was
 * required to *exist* by `failure-has-problems` and required to be *used* by nothing.
 *
 * Handing the factories out as a receiver closes that. Only [Problems.of] and [FailureTranslator.translate]
 * run with an `Answers` in scope, because only the renderer has one and it is the renderer that calls them.
 * Outside those two methods a module has no way to make a `Problem` at all — not a discouraged way, none.
 *
 * ### Why the set is closed
 *
 * Twelve HTTP meanings, no parameters for a status or a `type`. A module picks the meaning and
 * supplies what only it knows: which field, which code, what to say. So `type` cannot drift into
 * a free string, two modules cannot describe one kind of failure differently, and slice 14 has
 * something enumerable to write into the specification.
 *
 * A thirteenth meaning means editing this interface, which is the point: adding one is a decision
 * about the API's contract, and it should appear in a diff of the platform. [malformed] was the
 * seventh, added when a live run showed a malformed body answering 500.
 */
@Suppress("TooManyFunctions") // One function per meaning, and the number of meanings is a decision of this interface.
public interface Answers {
    /**
     * Could not be read at all: 400. A body that is not the JSON it claims to be, a media type
     * nothing can parse.
     *
     * Distinct from [invalid] on purpose, and the difference is whose fault the client should
     * conclude it is: 400 means "I could not understand you", 422 means "I understood and refused".
     * Added after a measurement — a malformed body was answering 500, so a client's typo read as
     * our outage and was logged as one.
     */
    public fun malformed(detail: String? = null): Problem

    /**
     * Understood and rejected: 422, naming the fields that offended.
     */
    public fun invalid(errors: List<FieldError>, detail: String? = null): Problem

    /**
     * The caller is known and may not do this: 403. Not 404 — whether hiding existence matters is
     * a judgement for the module, which says so by choosing [missing] instead.
     */
    public fun forbidden(detail: String? = null): Problem

    /**
     * Nobody is signed in and this needs somebody: 401, type `sign-in-required`. The client sends the
     * person to sign in.
     */
    public fun signInRequired(detail: String? = null): Problem

    /**
     * The request carried a session that has ended: 401, type `session-expired` (ADR-084). Told apart
     * from [signInRequired] because the client signs the person in again over the page they are on,
     * instead of leaving it.
     */
    public fun sessionExpired(detail: String? = null): Problem

    /**
     * The person is signed in and may do this, but must first prove who they are again: 403, type
     * `step-up-required` (ADR-092). Told apart from [forbidden] because the client reacts by asking
     * the person to confirm, and then repeats the request.
     */
    public fun stepUpRequired(detail: String? = null): Problem

    /**
     * Nothing here to act on: 404.
     */
    public fun missing(detail: String? = null): Problem

    /**
     * The request disagrees with the current state: 409. A concurrent edit, a duplicate a unique
     * index refused, a state machine that has already moved on.
     */
    public fun conflicting(detail: String? = null): Problem

    /**
     * What the request was about is over and will not come back: 410, type `gone`. A sign-in or
     * confirmation that ran out or was closed (ADR-093). Told apart from [missing] because the client
     * starts again instead of looking elsewhere.
     */
    public fun gone(detail: String? = null): Problem

    /**
     * Too many wrong answers lately, and the next one is not looked at yet: 429, type `slow-down`
     * (ADR-093). The route says how long to wait with `Retry-After`; the problem carries no number of
     * its own, so the header is the one place that says it.
     */
    public fun slowDown(detail: String? = null): Problem

    /**
     * A dependency is down and the request may be retried: 503.
     */
    public fun unavailable(detail: String? = null): Problem

    /**
     * Nobody predicted this, so it says nothing: 500 with no detail at all.
     *
     * The emptiness is the feature. This is what an escaped exception becomes, and an exception's
     * message carries hosts, ports, table names and occasionally credentials (§17). There is no
     * parameter here to leak them through.
     */
    public fun unexpected(): Problem
}
