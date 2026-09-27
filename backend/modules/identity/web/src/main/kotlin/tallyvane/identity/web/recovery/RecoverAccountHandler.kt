package tallyvane.identity.web.recovery

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.recovery.RecoverAccountRequest
import tallyvane.identity.application.recovery.RecoverAccountUseCase
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.login.SignInResponses
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret

internal class RecoverAccountHandler(
    private val recover: RecoverAccountUseCase,
    private val responses: SignInResponses,
    private val authenticationProblems: AuthenticationProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/recovery") {
            val body = call.receive<RecoverAccountBody>()
            val validation = FieldValidation.Accumulator()
            val email = validation.field("email") { Email(body.email) }
            val recoveryCode = validation.field("recoveryCode") {
                require(body.recoveryCode.isNotBlank()) { "Enter a recovery code." }
                Secret(body.recoveryCode)
            }
            val password = validation.field("newPassword") {
                val length = body.newPassword.codePointCount(0, body.newPassword.length)
                require(length in 15..128) { "Use 15 to 128 characters." }
                Secret(body.newPassword)
            }
            val device = validation.field("device") { DeviceLabel(body.device) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            responses.respond(
                call,
                recover.recover(
                    RecoverAccountRequest(
                        requireNotNull(email),
                        requireNotNull(recoveryCode),
                        requireNotNull(password),
                        requireNotNull(device),
                    ),
                ),
                authenticationProblems,
            )
        }
    }
}
