package tallyvane.sessions.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule
import tallyvane.sessions.application.DeviceOutcome
import tallyvane.sessions.application.DeviceWords
import tallyvane.sessions.application.ListDevicesUseCase

/**
 * The devices a person is signed in on (ADR-090).
 *
 * ```
 * GET /api/v1/devices   the live sessions of the signed-in person, the most recently used first
 * ```
 *
 * Never cached: the next person on the same browser must not be shown this one's devices. These routes
 * are not under `/sessions`, which is public so a person can start one: the edge opens a public path
 * and everything beneath it.
 */
internal class DeviceListRoutes(
    private val list: ListDevicesUseCase,
    private val session: SessionCookie,
    private val problems: DeviceProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/devices")

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (val outcome = list.list(session.secretIn(call))) {
                is DeviceOutcome.Listed -> call.respond(shownBy(outcome))
                is DeviceOutcome.Failed -> call.respond(Refused(outcome, problems))
                is DeviceOutcome.Done -> error("Listing devices does not end in a change.")
            }
        }
    }

    private fun shownBy(outcome: DeviceOutcome.Listed): DevicesShown {
        val words = DeviceWords()
        val shown = mutableListOf<DeviceShown>()
        outcome.writeTo { id, browser, platform, mobile, name, authenticatedAt, lastActiveAt, current ->
            shown += DeviceShown(
                id.value.toString(),
                words.of(browser),
                words.of(platform),
                mobile,
                name,
                authenticatedAt.toString(),
                lastActiveAt.toString(),
                current,
            )
        }
        return DevicesShown(shown)
    }

    override fun toString(): String = "DeviceListRoutes"
}
