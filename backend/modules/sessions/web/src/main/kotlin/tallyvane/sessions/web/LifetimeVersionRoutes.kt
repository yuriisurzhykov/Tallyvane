package tallyvane.sessions.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface
import tallyvane.sessions.application.AddLifetimeVersionUseCase
import tallyvane.sessions.application.LifetimeVersionAdded

/**
 * Proposing a new version of the lifetimes (ADR-098).
 *
 * ```
 * POST /api/v1/lifetime-versions   {client, idle_millis, absolute_millis, freshness_millis, comment}
 * ```
 *
 * `201` with the number the version was given; it is not in force until it is activated. Lifetimes outside the
 * bounds, or without a comment, are `422` naming every field that is wrong. Served on the administrators' site only.
 */
internal class LifetimeVersionRoutes(
    private val add: AddLifetimeVersionUseCase,
    private val problems: LifetimeVersionProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/lifetime-versions")

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.post {
            val proposal = call.receive<LifetimeProposal>()
            val administrator = AccountId(Requester(call).account())
            when (val outcome = add.add(administrator, proposal.lifetimes(), proposal.comment)) {
                is LifetimeVersionAdded.Added -> call.respond(HttpStatusCode.Created, madeBy(outcome))
                is LifetimeVersionAdded.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun madeBy(outcome: LifetimeVersionAdded.Added): LifetimeVersionMade {
        val told = mutableListOf<LifetimeVersionMade>()
        outcome.writeTo { client, number -> told += LifetimeVersionMade(client, number) }
        return told.single()
    }

    override fun toString(): String = "LifetimeVersionRoutes"
}
