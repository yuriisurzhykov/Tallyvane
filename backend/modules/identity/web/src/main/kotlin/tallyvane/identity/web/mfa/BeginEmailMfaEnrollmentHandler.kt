package tallyvane.identity.web.mfa

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.BeginEmailMfaEnrollmentUseCase
import tallyvane.identity.web.login.AuthenticationFailure
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret

internal class BeginEmailMfaEnrollmentHandler(
    private val begin: BeginEmailMfaEnrollmentUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val authenticationProblems: AuthenticationProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/email/enroll") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            val body = call.receive<EmailMfaEnrollmentBody>()
            val validation = FieldValidation.Accumulator()
            val password = validation.field("currentPassword") { Secret(body.currentPassword) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            val challengeId = begin.begin(identity.userId, password!!)
            if (challengeId == null) {
                call.respond(Refused(AuthenticationFailure.InvalidCredential, authenticationProblems))
            } else {
                call.respond(HttpStatusCode.Accepted, EmailMfaChallengeResponseBody(challengeId.toString()))
            }
        }
    }
}
