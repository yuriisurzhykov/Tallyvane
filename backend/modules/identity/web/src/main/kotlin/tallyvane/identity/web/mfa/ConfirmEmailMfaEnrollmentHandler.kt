package tallyvane.identity.web.mfa

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.ConfirmEmailMfaEnrollmentUseCase
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret
import kotlin.uuid.Uuid

internal class ConfirmEmailMfaEnrollmentHandler(
    private val confirm: ConfirmEmailMfaEnrollmentUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val factors: SecondFactorProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/email/confirm") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            val body = call.receive<EmailMfaConfirmBody>()
            val validation = FieldValidation.Accumulator()
            val challengeId = validation.field("challengeId") { Uuid.parse(body.challengeId) }
            val code = validation.field("code") { require(body.code.length == 6); Secret(body.code) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            if (!confirm.confirm(identity.userId, challengeId!!, code!!)) {
                call.respond(Refused(SecondFactorFailure.WrongCode, factors))
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
