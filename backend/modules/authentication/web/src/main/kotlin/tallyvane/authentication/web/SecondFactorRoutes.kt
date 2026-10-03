package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.authentication.application.SecondFactorShown
import tallyvane.authentication.application.ShowSecondFactorUseCase
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule

/**
 * What the signed-in person has set up as a second factor.
 *
 * ```
 * GET /api/v1/second-factor   off, active or retired, and how many recovery codes are left
 * ```
 *
 * Closed to everyone but a signed-in person. Never cached.
 */
internal class SecondFactorRoutes(private val show: ShowSecondFactorUseCase) : RouteModule {
    override val basePath: BasePath = BasePath("/second-factor")

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(show.show(AccountId(Requester(call).account())).reportTo(Telling()))
        }
    }

    override fun toString(): String = "SecondFactorRoutes"

    private class Telling : SecondFactorShown.Report<SecondFactorState> {
        override fun off(): SecondFactorState = SecondFactorState("off", 0)

        override fun active(recoveryCodesLeft: Int): SecondFactorState = SecondFactorState("active", recoveryCodesLeft)

        override fun retired(recoveryCodesLeft: Int): SecondFactorState =
            SecondFactorState("retired", recoveryCodesLeft)
    }
}
