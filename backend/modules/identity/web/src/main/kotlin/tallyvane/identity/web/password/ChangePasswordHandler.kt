package tallyvane.identity.web.password

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.password.ChangePasswordUseCase
import tallyvane.identity.web.login.AuthenticationFailure
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused

internal class ChangePasswordHandler(
    private val change: ChangePasswordUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val validationProblems: RequestValidationProblems,
    private val authenticationProblems: AuthenticationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/account/password") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            val body = call.receive<ChangePasswordBody>()
            val validation = FieldValidation.Accumulator()
            val next = validation.field("newPassword") {
                val length = body.newPassword.codePointCount(0, body.newPassword.length)
                require(length in 15..128) { "Use 15 to 128 characters." }
                body.newPassword
            }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            if (!change.change(
                    identity.userId,
                    identity.sessionId,
                    call.request.headers["X-Action-Proof"],
                    next!!,
                )
            ) {
                call.respond(Refused(AuthenticationFailure.InvalidCredential, authenticationProblems))
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
