package tallyvane.identity.web.login

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.email.SignInWithEmailCodeRequest
import tallyvane.identity.application.email.SignInWithEmailCodeUseCase
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret
import kotlin.uuid.Uuid

internal class EmailSignInHandler(
    private val signIn: SignInWithEmailCodeUseCase,
    private val responses: SignInResponses,
    private val authenticationProblems: AuthenticationProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/login/email/verify") {
            val body = call.receive<VerifyEmailSignInCodeBody>()
            val validation = FieldValidation.Accumulator()
            val challengeId = validation.field("challengeId") { Uuid.parse(body.challengeId) }
            val email = validation.field("email") { Email(body.email) }
            val code = validation.field("code") { Secret(body.code) }
            val device = validation.field("device") { DeviceLabel(body.device) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            val outcome = signIn.signIn(SignInWithEmailCodeRequest(challengeId!!, email!!, code!!, device!!))
            responses.respond(call, outcome, authenticationProblems)
        }
    }
}
