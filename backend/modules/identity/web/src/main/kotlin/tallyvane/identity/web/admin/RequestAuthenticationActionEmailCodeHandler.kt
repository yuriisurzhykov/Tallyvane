package tallyvane.identity.web.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.RequestAuthenticationActionEmailCodeUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.web.login.AuthenticationFailure
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.EmailChallengeResponseBody
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused

internal class RequestAuthenticationActionEmailCodeHandler(
    private val request: RequestAuthenticationActionEmailCodeUseCase,
    private val current: CurrentPrincipal,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/account/action-proof/email-code") { requestEmailCode(call) }
    }

    private suspend fun requestEmailCode(call: ApplicationCall) {
        val identity = current.resolve(call) ?: return
        val body = call.receive<RequestActionProofEmailCodeBody>()
        val validation = FieldValidation.Accumulator()
        val action = validation.field("action") { AuthenticationAction.valueOf(body.action) }
        val kind = validation.field("kind") { AuthenticationTokenKind.valueOf(body.kind) }
        val errors = validation.errorsOrNull()
        if (errors != null) {
            call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
            return
        }
        val challenge = request.request(
            identity.userId,
            identity.sessionId,
            requireNotNull(action),
            requireNotNull(kind),
        )
        if (challenge == null) {
            call.respond(Refused(AuthenticationFailure.InvalidCredential, AuthenticationProblems()))
        } else {
            call.respond(HttpStatusCode.Accepted, EmailChallengeResponseBody(challenge.toString()))
        }
    }
}
