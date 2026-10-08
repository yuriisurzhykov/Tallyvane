package tallyvane.platform.http

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.host
import tallyvane.platform.kernel.Surface
import java.net.URI

/**
 * Tells which [Surface] a request came through, from the `Host` the proxy passed on (ADR-097).
 *
 * Only the host says it: a path or a body can be written by anyone, and nginx sets `Host` from the name
 * the request was routed by. A host that is neither door's, such as `localhost` in a probe or a test, is
 * the console's, which is the door with less power.
 *
 * @param appOrigin The console's origin, such as `https://app.tallyvane.com`. No trailing slash.
 * @param adminOrigin The administrators' origin, such as `https://admin.tallyvane.com`. No trailing slash.
 */
public class Surfaces(private val appOrigin: String, private val adminOrigin: String) {
    private val adminHost: String = URI(adminOrigin).host

    init {
        require(URI(appOrigin).host != adminHost) {
            "The console and the administrators' site must be told apart by their hosts, " +
                "but both are $adminHost."
        }
    }

    /**
     * The door [call] came through.
     */
    public fun of(call: ApplicationCall): Surface = if (call.request.host() == adminHost) Surface.Admin else Surface.App

    /**
     * The origin of [surface]: where its pages are served, and what an unsafe request that came through it
     * must say it came from.
     */
    public fun originOf(surface: Surface): String = when (surface) {
        Surface.App -> appOrigin
        Surface.Admin -> adminOrigin
    }

    override fun toString(): String = "Surfaces(appOrigin=$appOrigin, adminOrigin=$adminOrigin)"
}
