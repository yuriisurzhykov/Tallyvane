package tallyvane.identity.web.registration

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tallyvane.identity.application.email.EmailChallenges
import tallyvane.identity.application.password.RegisterWithPasswordRequest
import tallyvane.identity.application.password.RegisterWithPasswordUseCase
import tallyvane.identity.domain.email.EmailChallengePurpose
import tallyvane.identity.domain.outcome.RegisterOutcome
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
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
    private val emailChallenges: EmailChallenges,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/register/password") {
            val body = call.receive<RegisterRequestBody>()
            val validation = FieldValidation.Accumulator()
            val email = validation.field("email") { Email(body.email) }
            val password = validation.field("password") { Secret(body.password) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }

            when (val outcome = useCase.register(RegisterWithPasswordRequest(email!!, password!!, body.displayName))) {
                is RegisterOutcome.Registered      -> {
                    val challenge = try {
                        emailChallenges.issue(
                            email,
                            EmailChallengePurpose.REGISTRATION,
                            outcome.userId.value.toString(),
                        )
                    } catch (_: Exception) {
                        // Account creation succeeds even while SMTP is unavailable; the user can resend later.
                        null
                    }
                    call.respond(
                        status = HttpStatusCode.Created,
                        message = RegisterResponseBody(outcome.userId.value.toString(), challenge?.id?.toString()),
                    )
                }

                is RegisterOutcome.EmailTaken      -> {
                    call.respond(
                        message = Refused(failure = RegisterFailure.EmailTaken, problems = registerProblems),
                    )
                }

                is RegisterOutcome.InvalidPassword -> {
                    call.respond(Refused(RegisterFailure.InvalidPassword, registerProblems))
                }
            }
        }
    }
}
