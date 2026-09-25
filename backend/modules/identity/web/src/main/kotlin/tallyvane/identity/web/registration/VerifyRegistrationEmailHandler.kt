package tallyvane.identity.web.registration

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import tallyvane.identity.application.email.VerifyRegistrationEmailUseCase
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.UserId
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import tallyvane.platform.kernel.Secret
import kotlin.uuid.Uuid

internal interface VerifyRegistrationEmailHandler {
    suspend fun handle(call: ApplicationCall)

    class Verification(
        private val useCase: VerifyRegistrationEmailUseCase,
        private val validationProblems: RequestValidationProblems,
        private val verificationProblems: RegistrationEmailProblems,
    ) : VerifyRegistrationEmailHandler {
        override suspend fun handle(call: ApplicationCall) {
            val body = call.receive<VerifyRegistrationEmailRequestBody>()
            val validation = FieldValidation.Accumulator()
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
}
