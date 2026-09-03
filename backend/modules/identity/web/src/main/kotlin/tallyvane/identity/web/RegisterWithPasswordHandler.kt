package tallyvane.identity.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.password.RegisterWithPasswordRequest
import tallyvane.identity.application.password.RegisterWithPasswordUseCase
import tallyvane.identity.domain.outcome.RegisterOutcome
import tallyvane.identity.domain.user.Email
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret

/**
 * `POST /api/v1/auth/register/password` — creates a new account. Does not sign in: `IssuedSession`'s
 * own KDoc names the paths that do, and registering is not one of them.
 */
internal class RegisterWithPasswordHandler(
    private val useCase: RegisterWithPasswordUseCase,
    private val registerProblems: RegisterProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/register/password") {
            val body = call.receive<RegisterRequestBody>()
            val validation = FieldValidation()
            val email = validation.field("email") { Email(body.email) }
            val password = validation.field("password") { Secret(body.password) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure(errors), validationProblems))
                return@post
            }

            when (val outcome = useCase.register(RegisterWithPasswordRequest(email!!, password!!, body.displayName))) {
                is RegisterOutcome.Registered ->
                    call.respond(HttpStatusCode.Created, RegisterResponseBody(outcome.userId.value.toString()))
                RegisterOutcome.EmailTaken -> call.respond(Refused(RegisterFailure.EmailTaken, registerProblems))
            }
        }
    }
}
