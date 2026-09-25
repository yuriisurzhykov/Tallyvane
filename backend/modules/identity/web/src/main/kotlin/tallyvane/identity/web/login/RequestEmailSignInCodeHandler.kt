package tallyvane.identity.web.login

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.email.RequestEmailSignInCodeUseCase
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.EmailChallengeResponseBody
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused

internal class RequestEmailSignInCodeHandler(
    private val requestCode: RequestEmailSignInCodeUseCase,
    private val authenticationProblems: AuthenticationProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/login/email/code") {
            val body = call.receive<RequestEmailSignInCodeBody>()
            val validation = FieldValidation.Accumulator()
            val email = validation.field("email") { Email(body.email) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            val challenge = requestCode.request(email!!)
            if (challenge == null) {
                call.response.headers.append("Retry-After", "60")
                call.respond(Refused(AuthenticationFailure.RateLimited, authenticationProblems))
            } else {
                call.respond(HttpStatusCode.Accepted, EmailChallengeResponseBody(challenge.id.toString()))
            }
        }
    }
}
