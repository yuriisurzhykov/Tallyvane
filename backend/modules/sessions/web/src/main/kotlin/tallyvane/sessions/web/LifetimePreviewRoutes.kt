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
import tallyvane.sessions.application.LifetimeVersionPreviewed
import tallyvane.sessions.application.PreviewLifetimeVersionUseCase

/**
 * What putting a kept version of the lifetimes in force would do (ADR-098).
 *
 * ```
 * GET /api/v1/lifetime-previews?client=browser&number=3   {sessions, would_end}
 * ```
 *
 * Writes nothing. Served on the administrators' site only. Never cached.
 */
internal class LifetimePreviewRoutes(
    private val preview: PreviewLifetimeVersionUseCase,
    private val problems: LifetimePreviewProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/lifetime-previews")

    override val door: Surface = Surface.Admin

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val client = call.request.queryParameters[CLIENT].orEmpty()
            val number = call.request.queryParameters[NUMBER]?.toIntOrNull() ?: NO_NUMBER
            when (val outcome = preview.preview(AccountId(Requester(call).account()), client, number)) {
                is LifetimeVersionPreviewed.Previewed -> call.respond(consequencesOf(outcome))
                is LifetimeVersionPreviewed.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun consequencesOf(outcome: LifetimeVersionPreviewed.Previewed): LifetimePreview {
        val told = mutableListOf<LifetimePreview>()
        outcome.writeTo { sessions, wouldEnd -> told += LifetimePreview(sessions, wouldEnd) }
        return told.single()
    }

    override fun toString(): String = "LifetimePreviewRoutes"

    private companion object {
        const val CLIENT = "client"
        const val NUMBER = "number"

        /**
         * What a number that is missing or is not a whole number is taken for: no version has it.
         */
        const val NO_NUMBER = 0
    }
}
