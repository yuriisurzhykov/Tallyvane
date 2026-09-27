package tallyvane.identity.web.login

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.application.login.ReadSignInOptionsUseCase
import tallyvane.identity.web.routing.AuthHandler

internal class ReadSignInOptionsHandler(private val options: ReadSignInOptionsUseCase) : AuthHandler {
    override fun install(route: Route) {
        route.get("/sign-in-options") {
            call.response.headers.append("Cache-Control", "no-store")
            call.respond(SignInOptionsBody(options.read().map { it.name }))
        }
    }
}
