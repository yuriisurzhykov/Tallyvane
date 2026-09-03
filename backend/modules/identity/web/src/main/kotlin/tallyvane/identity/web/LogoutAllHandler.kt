package tallyvane.identity.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.session.RevokeAllSessionsUseCase

/**
 * `POST /api/v1/auth/logout-all` — signs every one of the caller's sessions out, this one
 * included, and clears both cookies.
 */
internal class LogoutAllHandler(
    private val useCase: RevokeAllSessionsUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val cookies: SessionCookies,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/logout-all") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            useCase.revokeAll(identity.userId)
            cookies.clear(call)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
