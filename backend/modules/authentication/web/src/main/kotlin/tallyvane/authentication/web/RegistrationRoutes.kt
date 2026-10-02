package tallyvane.authentication.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.RegisterOutcome
import tallyvane.authentication.application.RegisterUseCase
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule

/**
 * Finishing the welcome form.
 *
 * ```
 * POST /api/v1/registration   the person's name and agreement; creates their account
 * ```
 *
 * Answers `204` and nothing else: registering grants no access (ADR-076). The sign-in it completes
 * waits to be redeemed for a session.
 */
internal class RegistrationRoutes(
    private val register: RegisterUseCase,
    private val cookie: AttemptCookie,
    private val problems: RegistrationProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/registration")

    override fun install(route: Route) {
        route.post {
            val form = call.receive<Registering>()
            when (val outcome = register.register(cookie.secretIn(call), form.name, form.agreed)) {
                is RegisterOutcome.Registered -> call.respond(HttpStatusCode.NoContent)
                is RegisterOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "RegistrationRoutes"
}
