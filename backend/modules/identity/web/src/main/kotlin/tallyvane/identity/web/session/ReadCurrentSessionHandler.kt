package tallyvane.identity.web.session

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal

/** `GET /api/v1/auth/session` — confirms the caller's access session without exposing session data. */
internal class ReadCurrentSessionHandler(
    private val currentPrincipal: CurrentPrincipal,
) : AuthHandler {
    override fun install(route: Route) {
        route.get("/session") {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            if (currentPrincipal.resolve(call) == null) return@get
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
