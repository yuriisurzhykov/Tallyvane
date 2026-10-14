package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.authentication.application.ListPolicyVersionsUseCase
import tallyvane.authentication.application.PolicyVersionsListed
import tallyvane.authentication.application.PolicyWords
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface

/**
 * The versions of the policy of one purpose (ADR-098).
 *
 * ```
 * GET /api/v1/policy-history?purpose=login   the versions newest first, with who made each and why
 * ```
 *
 * Served on the administrators' site only. Never cached.
 */
internal class PolicyHistoryRoutes(
    private val list: ListPolicyVersionsUseCase,
    private val words: PolicyWords,
    private val problems: PolicyHistoryProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/policy-history")

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val purpose = call.request.queryParameters[PURPOSE].orEmpty()
            when (val outcome = list.list(AccountId(Requester(call).account()), purpose)) {
                is PolicyVersionsListed.Listed -> {
                    val reading = PolicyHistory.Reading(words)
                    outcome.writeTo(reading)
                    call.respond(reading.of(purpose))
                }
                is PolicyVersionsListed.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "PolicyHistoryRoutes"

    private companion object {
        const val PURPOSE = "purpose"
    }
}
