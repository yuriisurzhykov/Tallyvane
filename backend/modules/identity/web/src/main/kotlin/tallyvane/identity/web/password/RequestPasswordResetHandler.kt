package tallyvane.identity.web.password

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.email.RequestPasswordResetUseCase
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

internal class RequestPasswordResetHandler(
    private val requestReset: RequestPasswordResetUseCase,
    private val validationProblems: RequestValidationProblems,
    private val authenticationProblems: AuthenticationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/password/forgot") {
            val body = call.receive<RequestPasswordResetBody>()
            val validation = FieldValidation.Accumulator()
            val email = validation.field("email") { Email(body.email) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            val challengeId = requestReset.request(email!!)
            if (challengeId == null) {
                call.response.headers.append("Retry-After", "60")
                call.respond(Refused(AuthenticationFailure.RateLimited, authenticationProblems))
            } else {
                call.respond(HttpStatusCode.Accepted, mapOf("challengeId" to challengeId.toString()))
            }
        }
    }
}
