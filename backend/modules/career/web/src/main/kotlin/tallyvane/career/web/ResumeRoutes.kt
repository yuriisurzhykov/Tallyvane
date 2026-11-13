package tallyvane.career.web

import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.career.application.ShowResumeUseCase
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule

internal class ResumeRoutes(private val show: ShowResumeUseCase) : RouteModule {
    override val basePath: BasePath = BasePath("/resume")

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val resume = show.show(Requester(call).account())
            val roles = resume.roles.map { RoleShown(it.company, it.position, it.period, it.bullets) }
            call.respond(ResumeShown(roles, resume.skills))
        }
    }

    override fun toString(): String = "ResumeRoutes"
}
