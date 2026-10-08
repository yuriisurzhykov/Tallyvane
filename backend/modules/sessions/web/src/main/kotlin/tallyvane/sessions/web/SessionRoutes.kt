package tallyvane.sessions.web

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.Surfaces
import tallyvane.sessions.application.OpenSessionUseCase
import tallyvane.sessions.application.Opened
import tallyvane.sessions.domain.UserAgent

/**
 * Exchanging a completed sign-in for a session.
 *
 * ```
 * POST /api/v1/sessions   the browser's `__Host-attempt` cookie, for a `__Host-session` cookie
 * ```
 *
 * The session keeps what the browser said about itself in `User-Agent` as the device it is on, to be shown
 * in the list of devices (ADR-090). The door the request came through decides whose sign-in is taken and
 * which kind of session is kept: on the administrators' site only an administrator's sign-in is taken, for an
 * account that holds the right (ADR-097).
 *
 * Answers `204`: the session is the cookie, and there is nothing in the body that a script could keep.
 * Public, because the person asking is not signed in yet; what they hold instead is a completed sign-in.
 * The response sets a cookie, so a repeat of its `Idempotency-Key` is not replayed: the sign-in was
 * single-use, and the repeat is told so.
 */
internal class SessionRoutes(
    private val open: OpenSessionUseCase,
    private val session: SessionCookie,
    private val spent: SpentAttemptCookie,
    private val problems: SessionProblems,
    private val surfaces: Surfaces,
) : RouteModule {
    override val basePath: BasePath = BasePath("/sessions")

    override val access: Access = Access.Public

    override fun install(route: Route) {
        route.post {
            when (
                val outcome = open.open(
                    AttemptSecret().of(call),
                    UserAgent(call.request.headers[HttpHeaders.UserAgent]),
                    surfaces.of(call),
                )
            ) {
                is Opened.Issued -> {
                    outcome.writeTo { secret, lasting -> session.give(call, secret, lasting) }
                    spent.clear(call)
                    call.respond(HttpStatusCode.NoContent)
                }
                is Opened.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "SessionRoutes"
}
