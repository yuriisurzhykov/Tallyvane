package tallyvane.authentication.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.ActivatePolicyVersionUseCase
import tallyvane.authentication.application.PolicyVersionActivated
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface

/**
 * Putting a kept version in force, or rolling back to an earlier one (ADR-098).
 *
 * ```
 * POST /api/v1/policy-activations   {purpose, number, comment}
 * ```
 *
 * `204` when it is in force. A dangerous act, so the edge asks for a recent proof of who the person is first
 * (ADR-092). A version of the administrators' sign-in that none of them could pass is `422` with the code
 * `would-lock-out-every-administrator` on the field `number`. Served on the administrators' site only.
 */
internal class PolicyActivationRoutes(
    private val activate: ActivatePolicyVersionUseCase,
    private val problems: PolicyActivationProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/policy-activations")

    override val access: Access = Access.SignedFresh

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.post {
            val wish = call.receive<PolicyActivation>()
            val administrator = AccountId(Requester(call).account())
            when (val outcome = activate.activate(administrator, wish.purpose, wish.number, wish.comment)) {
                is PolicyVersionActivated.Activated -> call.respond(HttpStatusCode.NoContent)
                is PolicyVersionActivated.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "PolicyActivationRoutes"
}
