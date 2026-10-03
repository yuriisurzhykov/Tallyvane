package tallyvane.sessions.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule
import tallyvane.sessions.application.DeviceOutcome
import tallyvane.sessions.application.SignOutOthersUseCase

/**
 * Signing out everywhere but here (ADR-090).
 *
 * ```
 * DELETE /api/v1/other-devices   ends every session of the person except the one asking
 * ```
 *
 * A dangerous act, so the edge asks for a recent proof of who the person is first (ADR-092).
 *
 * Always `204` for a signed-in person, whether there was another session or not.
 */
internal class OtherDevicesSignOutRoutes(
    private val signOutOthers: SignOutOthersUseCase,
    private val session: SessionCookie,
    private val problems: DeviceProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/other-devices")

    override val access: Access = Access.SignedFresh

    override fun install(route: Route) {
        route.delete {
            when (val outcome = signOutOthers.signOutOthers(session.secretIn(call))) {
                is DeviceOutcome.Done -> call.respond(HttpStatusCode.NoContent)
                is DeviceOutcome.Failed -> call.respond(Refused(outcome, problems))
                is DeviceOutcome.Listed -> error("Signing out does not list devices.")
            }
        }
    }

    override fun toString(): String = "OtherDevicesSignOutRoutes"
}
