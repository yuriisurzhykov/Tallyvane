package tallyvane.authentication.web

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.BeginStepUpUseCase
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.Surfaces

/**
 * Starting a confirmation of a dangerous act, as a person who is signed in (ADR-092).
 *
 * ```
 * POST /api/v1/google-step-up   the attempt begins; the answer is where to send the browser
 * ```
 *
 * Public, like the sign-in it resembles: what this does is begin an attempt, which proves nothing by
 * itself and can be begun by anyone. It matters only when the session that holds the finished attempt
 * takes it (`POST /step-ups`), and that route is closed to everyone but a signed-in person.
 */
internal class StepUpRoutes(
    private val begin: BeginStepUpUseCase,
    private val cookie: AttemptCookie,
    private val surfaces: Surfaces,
) : RouteModule {
    override val basePath: BasePath = BasePath("/google-step-up")

    override val access: Access = Access.Public

    override fun install(route: Route) {
        route.post {
            val told = mutableListOf<String>()
            begin.begin(surfaces.of(call)).writeTo { attempt, address ->
                cookie.give(call, attempt)
                told += address
            }
            call.respond(SignInStarted(told.single()))
        }
    }

    override fun toString(): String = "StepUpRoutes"
}
