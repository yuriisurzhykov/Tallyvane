package tallyvane.identity.web.password

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.email.ResetPasswordRequest
import tallyvane.identity.application.email.ResetPasswordUseCase
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.login.AuthenticationFailure
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret
import kotlin.uuid.Uuid

internal class ResetPasswordHandler(
    private val reset: ResetPasswordUseCase,
    private val validationProblems: RequestValidationProblems,
    private val authenticationProblems: AuthenticationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/password/reset") {
            val body = call.receive<CompletePasswordResetBody>()
            val validation = FieldValidation.Accumulator()
            val challengeId = validation.field("challengeId") { Uuid.parse(body.challengeId) }
            val email = validation.field("email") { Email(body.email) }
            val code = validation.field("code") {
                require(body.code.matches(Regex("[0-9]{6}"))) { "Enter the six-digit code." }
                Secret(body.code)
            }
            val password = validation.field("newPassword") {
                val length = body.newPassword.codePointCount(0, body.newPassword.length)
                require(length in 15..128) { "Use 15 to 128 characters." }
                Secret(body.newPassword)
            }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            if (!reset.reset(ResetPasswordRequest(challengeId!!, email!!, code!!, password!!))) {
                call.respond(Refused(AuthenticationFailure.InvalidCredential, authenticationProblems))
            } else {
                call.respond(HttpStatusCode.OK, mapOf("status" to "completed"))
            }
        }
    }
}
