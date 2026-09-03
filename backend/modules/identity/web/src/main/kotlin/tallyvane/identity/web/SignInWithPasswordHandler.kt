package tallyvane.identity.web

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.password.SignInWithPasswordRequest
import tallyvane.identity.application.password.SignInWithPasswordUseCase
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret

/**
 * `POST /api/v1/auth/login/password` — checks a password credential and, if valid, issues a
 * session cookie via [SignInResponses].
 */
internal class SignInWithPasswordHandler(
    private val useCase: SignInWithPasswordUseCase,
    private val responses: SignInResponses,
    private val authenticationProblems: AuthenticationProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/login/password") {
            val body = call.receive<SignInWithPasswordRequestBody>()
            val validation = FieldValidation()
            val email = validation.field("email") { Email(body.email) }
            val password = validation.field("password") { Secret(body.password) }
            val device = validation.field("device") { DeviceLabel(body.device) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure(errors), validationProblems))
                return@post
            }

            val outcome = useCase.signIn(SignInWithPasswordRequest(email!!, password!!, device!!))
            responses.respond(call, outcome, authenticationProblems)
        }
    }
}
