package tallyvane.career.web

import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.career.application.ShowHomeUseCase
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule

internal class HomeRoutes(private val show: ShowHomeUseCase) : RouteModule {
    override val basePath: BasePath = BasePath("/home")

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val home = show.show(Requester(call).account())
            val stops = home.route.map {
                StopShown(it.skill, it.name, it.importance, it.level.name.lowercase(), it.evidence)
            }
            call.respond(HomeShown(home.step, home.subject, home.detail, home.goal, home.goalName, stops, home.stories))
        }
    }

    override fun toString(): String = "HomeRoutes"
}
