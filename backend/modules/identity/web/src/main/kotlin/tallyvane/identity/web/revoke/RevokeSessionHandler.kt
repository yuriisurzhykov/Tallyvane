package tallyvane.identity.web.revoke

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tallyvane.identity.application.session.RevokeSessionOutcome
import tallyvane.identity.application.session.RevokeSessionUseCase
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.session.SessionFailure
import tallyvane.identity.web.session.SessionProblems
import tallyvane.identity.web.shared.CurrentPrincipal
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
                is RevokeSessionOutcome.Revoked  -> call.respond(HttpStatusCode.NoContent)
                is RevokeSessionOutcome.NotFound -> call.respond(Refused(SessionFailure.SessionNotFound, problems))
            }
        }
    }
}
