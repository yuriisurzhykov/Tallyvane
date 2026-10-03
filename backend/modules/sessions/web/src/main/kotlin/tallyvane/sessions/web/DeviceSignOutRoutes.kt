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
import tallyvane.sessions.application.RevokeDeviceUseCase

/**
 * Signing out on one device (ADR-090).
 *
 * ```
 * DELETE /api/v1/device/{id}   ends that session; `404` if it is not one of the person's
 * ```
 *
 * A dangerous act, so the edge asks for a recent proof of who the person is first (ADR-092): ending
 * the session one is using is `DELETE /session`, which asks for nothing.
 *
 * An id that is not an id at all, and one that names a stranger's session, answer as one that names
 * nothing: asking teaches nothing about anyone else's devices.
 */
internal class DeviceSignOutRoutes(
    private val revoke: RevokeDeviceUseCase,
    private val session: SessionCookie,
    private val problems: DeviceProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/device")

    override val access: Access = Access.SignedFresh

    override fun install(route: Route) {
        route.delete("/{id}") {
            val device = DeviceId().of(call)
            val outcome = device?.let { revoke.revoke(session.secretIn(call), it) }
                ?: DeviceOutcome.Failed.NoSuchDevice()
            when (outcome) {
                is DeviceOutcome.Done -> call.respond(HttpStatusCode.NoContent)
                is DeviceOutcome.Failed -> call.respond(Refused(outcome, problems))
                is DeviceOutcome.Listed -> error("Signing out does not list devices.")
            }
        }
    }

    override fun toString(): String = "DeviceSignOutRoutes"
}
