package tallyvane.platform.http

import tallyvane.platform.kernel.Failure

/**
 * The ways the edge refuses a request before any route sees it, because of who it comes from or where.
 *
 * Each is answered through [AccessProblems], so the contract of ADR-062 holds for these as it does for a
 * module's own failures.
 */
internal sealed interface AccessFailure : Failure {
    /**
     * The route is for signed-in people and the request carried no credential.
     */
    data object SignInRequired : AccessFailure

    /**
     * The request carried a credential that is no longer good.
     */
    data object SessionExpired : AccessFailure

    /**
     * The route is a dangerous act, and the person has not proved who they are recently enough for it
     * (ADR-092). Told apart from the 401s because the person is still signed in: they confirm and go on.
     */
    data object StepUpRequired : AccessFailure

    /**
     * An unsafe request from a page that is not ours (ADR-080).
     */
    data object ForeignOrigin : AccessFailure
}
