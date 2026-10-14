package tallyvane.authentication.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.AddPolicyVersionUseCase
import tallyvane.authentication.application.PolicyVersionAdded
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface

/**
 * Proposing a new version of a policy (ADR-098).
 *
 * ```
 * POST /api/v1/policy-versions   {purpose, steps, attempt_lifetime_millis, max_failures, first_delay_millis, comment}
 * ```
 *
 * `201` with the number the version was given; it is not in force until it is activated. A policy outside the
 * bounds, or without a comment, is `422` naming every field that is wrong. Served on the administrators' site only.
 */
internal class PolicyVersionRoutes(
    private val add: AddPolicyVersionUseCase,
    private val problems: PolicyVersionProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/policy-versions")

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.post {
            val proposal = call.receive<PolicyProposal>()
            when (val outcome = add.add(AccountId(Requester(call).account()), proposal.policy(), proposal.comment)) {
                is PolicyVersionAdded.Added -> call.respond(HttpStatusCode.Created, madeBy(outcome))
                is PolicyVersionAdded.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun madeBy(outcome: PolicyVersionAdded.Added): PolicyVersionMade {
        val told = mutableListOf<PolicyVersionMade>()
        outcome.writeTo { purpose, number -> told += PolicyVersionMade(purpose, number) }
        return told.single()
    }

    override fun toString(): String = "PolicyVersionRoutes"
}
