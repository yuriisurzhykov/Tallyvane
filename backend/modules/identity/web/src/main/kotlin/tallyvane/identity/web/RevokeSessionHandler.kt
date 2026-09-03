package tallyvane.identity.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import tallyvane.identity.application.session.RevokeSessionOutcome
import tallyvane.identity.application.session.RevokeSessionUseCase
import tallyvane.identity.domain.session.SessionId
import tallyvane.platform.http.Refused
import kotlin.uuid.Uuid

/**
 * `DELETE /api/v1/auth/sessions/{id}` — signs one of the caller's own devices out, per the list
 * `GET /api/v1/auth/sessions` shows.
 */
internal class RevokeSessionHandler(
    private val useCase: RevokeSessionUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val problems: SessionProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.delete("/sessions/{id}") {
            val identity = currentPrincipal.resolve(call) ?: return@delete
            val sessionId = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            if (sessionId == null) {
                call.respond(Refused(SessionFailure.SessionNotFound, problems))
                return@delete
            }

            when (useCase.revoke(identity.userId, SessionId(sessionId))) {
                RevokeSessionOutcome.Revoked -> call.respond(HttpStatusCode.NoContent)
                RevokeSessionOutcome.NotFound -> call.respond(Refused(SessionFailure.SessionNotFound, problems))
            }
        }
    }
}
