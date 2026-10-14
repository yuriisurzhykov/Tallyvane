package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.authentication.application.PolicyVersionPreviewed
import tallyvane.authentication.application.PreviewPolicyVersionUseCase
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Surface

/**
 * What putting a kept version in force would do (ADR-098).
 *
 * ```
 * GET /api/v1/policy-previews?purpose=login&number=3   {people, cannot_pass}
 * ```
 *
 * Writes nothing. Served on the administrators' site only. Never cached.
 */
internal class PolicyPreviewRoutes(
    private val preview: PreviewPolicyVersionUseCase,
    private val problems: PolicyPreviewProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/policy-previews")

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val purpose = call.request.queryParameters[PURPOSE].orEmpty()
            val number = call.request.queryParameters[NUMBER]?.toIntOrNull() ?: NO_NUMBER
            when (val outcome = preview.preview(AccountId(Requester(call).account()), purpose, number)) {
                is PolicyVersionPreviewed.Previewed -> call.respond(consequencesOf(outcome))
                is PolicyVersionPreviewed.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun consequencesOf(outcome: PolicyVersionPreviewed.Previewed): PolicyPreview {
        val told = mutableListOf<PolicyPreview>()
        outcome.writeTo { people, cannotPass -> told += PolicyPreview(people, cannotPass) }
        return told.single()
    }

    override fun toString(): String = "PolicyPreviewRoutes"

    private companion object {
        const val PURPOSE = "purpose"
        const val NUMBER = "number"

        /**
         * What a number that is missing or is not a whole number is taken for: no version has it.
         */
        const val NO_NUMBER = 0
    }
}
