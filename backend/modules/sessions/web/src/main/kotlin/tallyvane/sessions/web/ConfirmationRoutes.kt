package tallyvane.sessions.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.Surfaces
import tallyvane.sessions.application.ConfirmStepUpUseCase
import tallyvane.sessions.application.Confirmed

/**
 * Taking a finished confirmation as the person's fresh proof (ADR-092).
 *
 * ```
 * POST /api/v1/step-ups   the browser's `__Host-attempt` cookie, for a session that counts it
 * ```
 *
 * Closed to everyone but a signed-in person, and open to one whose last proof is stale: it is how they
 * get a fresh one. Answers `204`: the session is the same one, with the same cookie. The cookie of the
 * confirmation is cleared in the same response, whatever came of it, since the confirmation is spent
 * either way. The response sets a cookie, so a repeat of its `Idempotency-Key` is not replayed.
 */
internal class ConfirmationRoutes(
    private val confirm: ConfirmStepUpUseCase,
    private val session: SessionCookie,
    private val spent: SpentAttemptCookie,
    private val problems: ConfirmationProblems,
    private val surfaces: Surfaces,
) : RouteModule {
    override val basePath: BasePath = BasePath("/step-ups")

    override fun install(route: Route) {
        route.post {
            val outcome = confirm.confirm(session.secretIn(call), AttemptSecret().of(call), surfaces.of(call))
            spent.clear(call)
            when (outcome) {
                is Confirmed.Done -> call.respond(HttpStatusCode.NoContent)
                is Confirmed.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "ConfirmationRoutes"
}
