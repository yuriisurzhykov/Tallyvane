package tallyvane.identity.web.logout

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.session.RevokeAllSessionsUseCase
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.SessionCookies

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
