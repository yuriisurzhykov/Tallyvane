package tallyvane.identity.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.application.WhoAmIOutcome
import tallyvane.identity.application.WhoAmIUseCase
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule

/**
 * Who the signed-in person is.
 *
 * ```
 * GET /api/v1/me   the account and the name the person is called by
 * ```
 *
 * Closed to everyone but a signed-in person, as every route is unless it says otherwise, so the
 * handler never meets a stranger. Never cached: the next person on the same browser must not be shown
 * this one.
 */
internal class MeRoutes(private val whoAmI: WhoAmIUseCase, private val problems: MeProblems) : RouteModule {
    override val basePath: BasePath = BasePath("/me")

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (val outcome = whoAmI.whoIs(AccountId(Requester(call).account()))) {
                is WhoAmIOutcome.Known -> call.respond(describedBy(outcome))
                is WhoAmIOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun describedBy(outcome: WhoAmIOutcome.Known): Described {
        val told = mutableListOf<Described>()
        outcome.writeTo { id, name -> told += Described(id.toString(), name) }
        return told.single()
    }

    override fun toString(): String = "MeRoutes"
}
