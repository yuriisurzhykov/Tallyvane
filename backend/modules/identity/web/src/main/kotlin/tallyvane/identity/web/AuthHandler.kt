package tallyvane.identity.web

import io.ktor.server.routing.Route

/**
 * One address under `/auth` and its own logic — never more than one use case behind it, the same
 * rule `web-one-usecase` states for a [tallyvane.platform.http.RouteModule] itself. [AuthRoutes]
 * exists precisely because that rule is a hard limit and `identity` needs far more than one
 * action behind a single shared base path: each handler here satisfies the rule on its own, and
 * [AuthRoutes] mounts as many of them as `identity` has actions, none of them individually
 * carrying more than one.
 */
internal interface AuthHandler {
    /**
     * Registers this handler's one address on [route], which is already mounted at `/auth`.
     */
    fun install(route: Route)
}
