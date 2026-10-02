package tallyvane.platform.http

import io.ktor.server.routing.Route

/**
 * The routes one module publishes, as §11.1 defines them.
 *
 * A module registers its own addresses and knows nobody else's. Without this, either one file
 * lists every address in the system — and every change to `jobs` edits it — or modules reach
 * into each other's routing.
 *
 * `install` takes Ktor's own [Route], so this contract names Ktor deliberately. ADR-050 says
 * so plainly: swapping the *engine* costs one line in the composition root, swapping Ktor
 * itself does not, and this signature is where that is decided.
 */
public interface RouteModule {
    /**
     * Where `app` mounts this module, under the `/api/v1` prefix it adds itself.
     */
    public val basePath: BasePath

    /**
     * Who may reach these routes. Closed unless a module says it is [Access.Public], so a route
     * nobody thought about is not one anybody can call (ADR-088).
     */
    public val access: Access
        get() = Access.Signed

    /**
     * Registers this module's addresses on [route], which is already mounted at [basePath].
     */
    public fun install(route: Route)
}
