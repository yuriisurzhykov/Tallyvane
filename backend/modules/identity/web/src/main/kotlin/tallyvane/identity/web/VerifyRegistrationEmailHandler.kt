package tallyvane.identity.web

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.application.ApplicationCall
import tallyvane.identity.application.email.VerifyRegistrationEmailUseCase
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.web.auth.RequestValidationFailure
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret
import kotlin.uuid.Uuid

internal class VerifyRegistrationEmailHandler(
    private val useCase: VerifyRegistrationEmailUseCase,
    private val validationProblems: RequestValidationProblems,
    private val verificationProblems: RegistrationEmailProblems,
) {
    suspend fun handle(call: ApplicationCall) {
        val body = call.receive<VerifyRegistrationEmailRequestBody>()
        val validation = FieldValidation()
        val userId = validation.field("userId") { UserId(Uuid.parse(body.userId)) }
        val challengeId = validation.field("challengeId") { Uuid.parse(body.challengeId) }
        val email = validation.field("email") { Email(body.email) }
        val code = validation.field("code") { Secret(body.code) }
        val errors = validation.errorsOrNull()
        if (errors != null) {
            call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
            return
        }
        if (useCase.verify(userId!!, challengeId!!, email!!, code!!)) {
            call.respond(io.ktor.http.HttpStatusCode.NoContent)
        } else {
            call.respond(Refused(RegistrationEmailFailure.InvalidCode, verificationProblems))
        }
    }
}
