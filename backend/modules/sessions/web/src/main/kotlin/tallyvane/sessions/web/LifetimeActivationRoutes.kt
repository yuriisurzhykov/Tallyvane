package tallyvane.sessions.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface
import tallyvane.sessions.application.ActivateLifetimeVersionUseCase
import tallyvane.sessions.application.LifetimeVersionActivated

/**
 * Putting a kept version of the lifetimes in force, or rolling back to an earlier one (ADR-098).
 *
 * ```
 * POST /api/v1/lifetime-activations   {client, number, comment}
 * ```
 *
 * `204` when it is in force. A dangerous act, so the edge asks for a recent proof of who the person is first
 * (ADR-092). Served on the administrators' site only.
 */
internal class LifetimeActivationRoutes(
    private val activate: ActivateLifetimeVersionUseCase,
    private val problems: LifetimeActivationProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/lifetime-activations")

    override val access: Access = Access.SignedFresh

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.post {
            val wish = call.receive<LifetimeActivation>()
            val administrator = AccountId(Requester(call).account())
            when (val outcome = activate.activate(administrator, wish.client, wish.number, wish.comment)) {
                is LifetimeVersionActivated.Activated -> call.respond(HttpStatusCode.NoContent)
                is LifetimeVersionActivated.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "LifetimeActivationRoutes"
}
