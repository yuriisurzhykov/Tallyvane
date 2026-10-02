package tallyvane.identity.web

import tallyvane.identity.application.WhoAmIUseCase
import tallyvane.platform.http.RouteModule

/**
 * Hands out the routes this module serves, for the composition root to mount. The route classes are
 * `internal`; one takes one use case (`web-one-usecase`).
 */
public class IdentityRoutesFactory {
    /**
     * `GET /me`: who the signed-in person is.
     */
    public fun me(whoAmI: WhoAmIUseCase): RouteModule = MeRoutes(whoAmI, MeProblems())

    override fun toString(): String = "IdentityRoutesFactory"
}
