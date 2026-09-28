package tallyvane.identity.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.session.RevokeSessionUseCase

/**
 * `POST /api/v1/auth/logout` — signs the caller's own current session out and clears both cookies.
 */
internal class LogoutHandler(
    private val useCase: RevokeSessionUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val cookies: SessionCookies,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/logout") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            // The outcome is not answered on: revoking a session that already turns out to be
            // gone (raced by a second tab's own logout) is still "you are signed out" from this
            // caller's point of view, and the cookies are cleared either way.
            useCase.revoke(identity.userId, identity.sessionId)
            cookies.clear(call)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
