package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.authentication.application.ShowRegistrationOutcome
import tallyvane.authentication.application.ShowRegistrationUseCase
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule

/**
 * What the welcome form starts from.
 *
 * ```
 * GET /api/v1/welcome   the name and address Google gave the person registering
 * ```
 *
 * Never cached: it is somebody's name and address, and the same address answers differently for the
 * next browser.
 */
internal class WelcomeRoutes(
    private val show: ShowRegistrationUseCase,
    private val cookie: AttemptCookie,
    private val problems: WelcomeProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/welcome")

    override val access: Access = Access.Public

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (val outcome = show.show(cookie.secretIn(call))) {
                is ShowRegistrationOutcome.Welcome -> call.respond(welcomedBy(outcome))
                is ShowRegistrationOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun welcomedBy(outcome: ShowRegistrationOutcome.Welcome): Welcomed {
        val told = mutableListOf<Welcomed>()
        outcome.writeTo { name, email -> told += Welcomed(name, email) }
        return told.single()
    }

    override fun toString(): String = "WelcomeRoutes"
}
