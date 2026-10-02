package tallyvane.platform.http

import tallyvane.platform.kernel.Failure

/**
 * The ways the edge refuses a request because of its `Idempotency-Key`, before or instead of running it.
 *
 * Each is answered through [IdempotencyProblems], so the contract of ADR-062 holds for these as it does
 * for a module's own failures: a [tallyvane.platform.http.problems.Problem] can only be made by a table.
 */
internal sealed interface IdempotencyFailure : Failure {
    /**
     * An unsafe request with no key at all.
     */
    data object Missing : IdempotencyFailure

    /**
     * A key that is not a UUID.
     */
    data object Malformed : IdempotencyFailure

    /**
     * A body the edge will not hold in memory to fingerprint.
     */
    data object TooLarge : IdempotencyFailure

    /**
     * The key was used before for a different request.
     */
    data object Reused : IdempotencyFailure

    /**
     * The work is being done by another request, and did not finish in the time allowed to wait.
     */
    data object InProgress : IdempotencyFailure

    /**
     * The work was done, and its answer cannot be given again.
     */
    data object Lost : IdempotencyFailure
}
