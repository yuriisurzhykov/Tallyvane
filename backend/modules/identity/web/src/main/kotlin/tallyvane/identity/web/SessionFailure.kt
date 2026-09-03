package tallyvane.identity.web

import tallyvane.platform.kernel.Failure

/**
 * Every reason a session-management route (`DELETE /auth/sessions/{id}`, `POST /auth/refresh`)
 * did not do what it was asked, grouped under one root so [SessionProblems] maps both with one
 * table.
 */
internal sealed interface SessionFailure : Failure {
    /**
     * [tallyvane.identity.application.session.RevokeSessionOutcome.NotFound]'s own web mapping —
     * covers both "no such session" and "belongs to someone else", per that outcome's own KDoc on
     * why the two are not distinguished.
     */
    data object SessionNotFound : SessionFailure

    /**
     * [tallyvane.identity.application.session.RefreshSessionOutcome.Invalid]'s own web mapping —
     * an unknown, already-consumed, or race-losing refresh token.
     */
    data object RefreshInvalid : SessionFailure

    /**
     * [tallyvane.identity.application.session.RefreshSessionOutcome.ReuseDetected]'s own web
     * mapping — the whole session was already revoked by the time this answer is sent.
     */
    data object RefreshReused : SessionFailure

    /**
     * A protected route's session cookie named no currently valid session — never issued, expired,
     * or revoked, the same three [tallyvane.identity.contract.PrincipalResolver.resolve] itself
     * does not distinguish.
     */
    data object NotAuthenticated : SessionFailure
}
