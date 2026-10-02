package tallyvane.sessions.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.RouteModule
import tallyvane.sessions.application.SignOutUseCase

/**
 * Signing out.
 *
 * ```
 * DELETE /api/v1/session   forgets the session the browser presents, and the browser's cookie
 * ```
 *
 * Always `204`, and public: whoever asks to be signed out is signed out, whether their session was good,
 * over, or never there, and a person whose session has lapsed can still clear their cookie.
 */
internal class SignOutRoutes(private val signOut: SignOutUseCase, private val session: SessionCookie) : RouteModule {
    override val basePath: BasePath = BasePath("/session")

    override val access: Access = Access.Public

    override fun install(route: Route) {
        route.delete {
            signOut.signOut(session.secretIn(call))
            session.clear(call)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    override fun toString(): String = "SignOutRoutes"
}
