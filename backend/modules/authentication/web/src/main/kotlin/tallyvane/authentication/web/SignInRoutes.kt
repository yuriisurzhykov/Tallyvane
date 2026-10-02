package tallyvane.authentication.web

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.BeginSignInUseCase
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.RouteModule

/**
 * Starting a sign-in.
 *
 * ```
 * POST /api/v1/google-sign-in   the attempt begins; the answer is where to send the browser
 * ```
 *
 * The attempt's secret goes into the `__Host-attempt` cookie of this very response, so the browser
 * holds it when Google sends the person back. A response that sets a cookie is never stored for a
 * repeat of its `Idempotency-Key`: a repeat is told the work was done, and starts again.
 */
internal class SignInRoutes(private val begin: BeginSignInUseCase, private val cookie: AttemptCookie) : RouteModule {
    override val basePath: BasePath = BasePath("/google-sign-in")

    override fun install(route: Route) {
        route.post {
            val told = mutableListOf<String>()
            begin.begin().writeTo { attempt, address ->
                cookie.give(call, attempt)
                told += address
            }
            call.respond(SignInStarted(told.single()))
        }
    }

    override fun toString(): String = "SignInRoutes"
}
