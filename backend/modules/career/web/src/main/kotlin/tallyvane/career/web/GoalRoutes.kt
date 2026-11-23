package tallyvane.career.web

import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import tallyvane.career.application.GoalOutcome
import tallyvane.career.application.SetGoalUseCase
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule

internal class GoalRoutes(private val set: SetGoalUseCase, private val problems: GoalProblems) : RouteModule {
    override val basePath: BasePath = BasePath("/goal")

    override fun install(route: Route) {
        route.put {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (val outcome = set.set(Requester(call).account(), call.receive<GoalChosen>().role)) {
                is GoalOutcome.Set -> call.respond(GoalChosen(outcome.role))
                is GoalOutcome.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "GoalRoutes"
}
