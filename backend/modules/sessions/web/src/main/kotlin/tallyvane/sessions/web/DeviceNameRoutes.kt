package tallyvane.sessions.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule
import tallyvane.sessions.application.DeviceOutcome
import tallyvane.sessions.application.RenameDeviceUseCase

/**
 * Giving a device a name its person will know it by (ADR-090).
 *
 * ```
 * PUT /api/v1/device-names/{id}   {"name": "Work laptop"}; `404` if it is not one of the person's
 * ```
 *
 * The name is a thing of its own that a device has, so naming is a `PUT` to it, and doing it twice leaves
 * the same name.
 */
internal class DeviceNameRoutes(
    private val rename: RenameDeviceUseCase,
    private val session: SessionCookie,
    private val problems: DeviceProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/device-names")

    override fun install(route: Route) {
        route.put("/{id}") {
            val naming = call.receive<DeviceNaming>()
            val device = DeviceId().of(call)
            val outcome = device?.let { rename.rename(session.secretIn(call), it, naming.name) }
                ?: DeviceOutcome.Failed.NoSuchDevice()
            when (outcome) {
                is DeviceOutcome.Done -> call.respond(HttpStatusCode.NoContent)
                is DeviceOutcome.Failed -> call.respond(Refused(outcome, problems))
                is DeviceOutcome.Listed -> error("Naming a device does not list devices.")
            }
        }
    }

    override fun toString(): String = "DeviceNameRoutes"
}
