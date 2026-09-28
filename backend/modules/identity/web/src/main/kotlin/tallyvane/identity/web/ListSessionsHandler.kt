package tallyvane.identity.web

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.application.session.ListSessionsUseCase

/**
 * `GET /api/v1/auth/sessions` — the caller's own "connected devices" list, revoked ones included.
 */
internal class ListSessionsHandler(
    private val useCase: ListSessionsUseCase,
    private val currentPrincipal: CurrentPrincipal,
) : AuthHandler {
    override fun install(route: Route) {
        route.get("/sessions") {
            val identity = currentPrincipal.resolve(call) ?: return@get
            val sessions = useCase.list(identity.userId).map { SessionResponseBody.of(it) }
            call.respond(sessions)
        }
    }
}
