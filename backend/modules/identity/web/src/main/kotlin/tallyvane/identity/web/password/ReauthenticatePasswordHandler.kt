package tallyvane.identity.web.password

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.password.ReauthenticateUseCase
import tallyvane.identity.web.login.AuthenticationFailure
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret

internal class ReauthenticatePasswordHandler(
    private val reauthenticate: ReauthenticateUseCase,
    private val current: CurrentPrincipal,
    private val problems: AuthenticationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/account/reauth/password") {
            val identity = current.resolve(call) ?: return@post
            val body = call.receive<ReauthenticatePasswordBody>()
            when (reauthenticate.password(identity.userId, identity.sessionId, Secret(body.password))) {
                ReauthenticateUseCase.Outcome.REAUTHENTICATED -> call.respond(HttpStatusCode.NoContent)
                ReauthenticateUseCase.Outcome.INVALID_CREDENTIAL,
                ReauthenticateUseCase.Outcome.PROVIDER_UNAVAILABLE,
                -> call.respond(Refused(AuthenticationFailure.InvalidCredential, problems))
            }
        }
    }
}
