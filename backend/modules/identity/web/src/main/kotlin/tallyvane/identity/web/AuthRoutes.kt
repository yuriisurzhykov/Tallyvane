package tallyvane.identity.web

import io.ktor.server.routing.Route
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.RouteModule

/**
 * Every address under `/api/v1/auth` `identity` answers, mounted at the one base path
 * [tallyvane.platform.http.Api] lets a module own. [handlers] is what actually does the work —
 * see [AuthHandler]'s own KDoc for why one [RouteModule] holds many of them instead of `identity`
 * needing one [BasePath] per action.
 */
internal class AuthRoutes(private val handlers: List<AuthHandler>) : RouteModule {
    override val basePath: BasePath = BasePath("/auth")

    override fun install(route: Route) {
        handlers.forEach { it.install(route) }
    }
}
