package tallyvane.platform.http

import tallyvane.platform.kernel.Failure

/**
 * A failure paired with the table that knows what it means — the only way a route answers with one.
 *
 * ```kotlin
 * when (val outcome = saveJob.save(command)) {
 *     is Saved  -> call.respond(HttpStatusCode.Created, JobCreated(outcome.id.value))
 *     is Failed -> call.respond(Refused(outcome, problems))
 * }
 * ```
 *
 * The type parameter is the enforcement. `Refused(failure, problems)` does not compile unless the
 * table maps that exact failure branch, so a route cannot answer a `jobs` failure with a
 * `documents` table, cannot answer without a table at all, and cannot invent a [Problem] — it has
 * no way to make one.
 *
 * Rendering happens later and elsewhere: the send pipeline recognises this, asks the table, and
 * writes the status, the `application/problem+json` type, the trace id and the log line. A route
 * arranges none of that, which is why none of it can be forgotten.
 */
public class Refused<F : Failure>(private val failure: F, private val problems: Problems<F>) {
    /**
     * Asks the table, with the receiver only the renderer possesses.
     *
     * Internal so the pairing cannot be unwrapped by a caller and re-answered differently, and so
     * the type parameter's erasure stays inside this class rather than leaking a star projection
     * into the pipeline.
     */
    internal fun problem(answers: Answers): Problem = with(problems) { with(answers) { of(failure) } }
}
