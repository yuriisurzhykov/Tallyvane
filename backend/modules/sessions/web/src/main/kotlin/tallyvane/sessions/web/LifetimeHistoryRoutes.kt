package tallyvane.sessions.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface
import tallyvane.sessions.application.LifetimeVersionsListed
import tallyvane.sessions.application.ListLifetimeVersionsUseCase

/**
 * The versions of the lifetimes of one kind of client (ADR-098).
 *
 * ```
 * GET /api/v1/lifetime-history?client=browser   the versions newest first, with who made each and why
 * ```
 *
 * Served on the administrators' site only. Never cached.
 */
internal class LifetimeHistoryRoutes(
    private val list: ListLifetimeVersionsUseCase,
    private val problems: LifetimeHistoryProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/lifetime-history")

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val client = call.request.queryParameters[CLIENT].orEmpty()
            when (val outcome = list.list(AccountId(Requester(call).account()), client)) {
                is LifetimeVersionsListed.Listed -> {
                    val reading = LifetimeHistory.Reading()
                    outcome.writeTo(reading)
                    call.respond(reading.of(client))
                }
                is LifetimeVersionsListed.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "LifetimeHistoryRoutes"

    private companion object {
        const val CLIENT = "client"
    }
}
