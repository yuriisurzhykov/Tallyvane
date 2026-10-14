package tallyvane.sessions.web

import tallyvane.platform.http.RouteModule
import tallyvane.sessions.application.ActivateLifetimeVersionUseCase
import tallyvane.sessions.application.AddLifetimeVersionUseCase
import tallyvane.sessions.application.ListLifetimeVersionsUseCase
import tallyvane.sessions.application.PreviewLifetimeVersionUseCase

/**
 * Hands out the routes of the administrators' session-lifetime screens (ADR-098), for the composition root to
 * mount. The route classes are `internal`; one takes one use case. All of them are served on the administrators' site
 * only.
 */
public class LifetimeRoutesFactory {
    /**
     * `GET /lifetime-history`: the versions of the lifetimes of one kind of client.
     */
    public fun history(list: ListLifetimeVersionsUseCase): RouteModule =
        LifetimeHistoryRoutes(list, LifetimeHistoryProblems())

    /**
     * `POST /lifetime-versions`: proposing a new version.
     */
    public fun versions(add: AddLifetimeVersionUseCase): RouteModule =
        LifetimeVersionRoutes(add, LifetimeVersionProblems())

    /**
     * `GET /lifetime-previews`: what putting a version in force would do.
     */
    public fun previews(preview: PreviewLifetimeVersionUseCase): RouteModule =
        LifetimePreviewRoutes(preview, LifetimePreviewProblems())

    /**
     * `POST /lifetime-activations`: putting a version in force.
     */
    public fun activations(activate: ActivateLifetimeVersionUseCase): RouteModule =
        LifetimeActivationRoutes(activate, LifetimeActivationProblems())

    override fun toString(): String = "LifetimeRoutesFactory"
}
