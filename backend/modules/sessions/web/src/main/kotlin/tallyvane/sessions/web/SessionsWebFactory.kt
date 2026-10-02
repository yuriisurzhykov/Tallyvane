package tallyvane.sessions.web

import tallyvane.platform.http.Callers
import tallyvane.platform.http.RouteModule
import tallyvane.sessions.application.AuthenticateUseCase
import tallyvane.sessions.application.OpenSessionUseCase
import tallyvane.sessions.application.SignOutUseCase

/**
 * Hands out what this module serves, for the composition root to mount. The route classes are
 * `internal`; one takes one use case (`web-one-usecase`), so this is where the root, which holds all of
 * them, asks for each.
 */
public class SessionsWebFactory {
    /**
     * `POST /sessions`: exchanging a completed sign-in for a session.
     */
    public fun open(open: OpenSessionUseCase): RouteModule =
        SessionRoutes(open, SessionCookie(), SpentAttemptCookie(), SessionProblems())

    /**
     * `DELETE /session`: signing out.
     */
    public fun signOut(signOut: SignOutUseCase): RouteModule = SignOutRoutes(signOut, SessionCookie())

    /**
     * How every request is recognised: by its session cookie.
     */
    public fun callers(authenticate: AuthenticateUseCase): Callers = SessionCallers(authenticate, SessionCookie())

    override fun toString(): String = "SessionsWebFactory"
}
