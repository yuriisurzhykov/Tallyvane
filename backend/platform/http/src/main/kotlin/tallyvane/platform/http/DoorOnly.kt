package tallyvane.platform.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import tallyvane.platform.kernel.Surface

/**
 * Answers `404` to a request that came through another door than the one the routes it is installed on belong to
 * (ADR-098), as if the routes were not there.
 *
 * Route-scoped, so routing decides which requests it sees and no spelling of a path can step around it. Installed
 * by [Api] for a module that names its [RouteModule.door], never by a module itself.
 */
internal val DoorOnly = createRouteScopedPlugin("DoorOnly", ::DoorOnlyConfig) {
    val door = pluginConfig.door
    val surfaces = pluginConfig.surfaces
    onCall { call ->
        if (surfaces.of(call) != door) {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

/**
 * What [DoorOnly] is told when it is installed: the door the routes belong to, and the doors there are.
 */
internal class DoorOnlyConfig {
    var door: Surface = Surface.Admin
    var surfaces: Surfaces = Surfaces("https://app.invalid", "https://admin.invalid")
}
