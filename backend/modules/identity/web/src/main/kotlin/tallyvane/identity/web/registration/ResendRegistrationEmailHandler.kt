package tallyvane.identity.web.registration

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.email.ResendRegistrationEmailUseCase
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused

internal class ResendRegistrationEmailHandler(
    private val resend: ResendRegistrationEmailUseCase,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/register/email/resend") {
            val body = call.receive<ResendRegistrationEmailBody>()
            val validation = FieldValidation.Accumulator()
            val email = validation.field("email") { Email(body.email) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            val challengeId = try {
                resend.resend(email!!)
            } catch (_: Exception) {
                // Keep registration recoverable during SMTP outages and the response account-neutral.
                null
            }
            call.respond(HttpStatusCode.Accepted, ResendRegistrationEmailResponseBody(challengeId?.toString()))
        }
    }
}
