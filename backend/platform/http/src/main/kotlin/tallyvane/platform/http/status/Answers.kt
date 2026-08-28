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
 * name it — which is why this one can still promise a module seven meanings and no way to invent an
 * eighth.
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
 * Seven HTTP meanings, no parameters for a status or a `type`. A module picks the meaning and
 * supplies what only it knows: which field, which code, what to say. So `type` cannot drift into
 * a free string, two modules cannot describe one kind of failure differently, and slice 14 has
 * something enumerable to write into the specification.
 *
 * An eighth meaning means editing this interface, which is the point: adding one is a decision
 * about the API's contract, and it should appear in a diff of the platform. [malformed] was the
 * seventh, added when a live run showed a malformed body answering 500.
 */
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
     * Nothing here to act on: 404.
     */
    public fun missing(detail: String? = null): Problem

    /**
     * The request disagrees with the current state: 409. A concurrent edit, a duplicate a unique
     * index refused, a state machine that has already moved on.
     */
    public fun conflicting(detail: String? = null): Problem

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
