package tallyvane.identity.web.mfa

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.RequestEmailMfaCodeUseCase
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import kotlin.uuid.Uuid

internal class RequestEmailMfaCodeHandler(
    private val request: RequestEmailMfaCodeUseCase,
    private val problems: SecondFactorProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/email/request") {
            val body = call.receive<EmailMfaRequestBody>()
            val validation = FieldValidation.Accumulator()
            val pendingId = validation.field("pendingId") { PendingAuthenticationId(Uuid.parse(body.pendingId)) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            val challengeId = request.request(pendingId!!)
            if (challengeId == null) {
                call.respond(Refused(SecondFactorFailure.RateLimited, problems))
            } else {
                call.respond(HttpStatusCode.Accepted, EmailMfaChallengeResponseBody(challengeId.toString()))
            }
        }
    }
}
