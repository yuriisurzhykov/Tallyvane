package tallyvane.identity.web.mfa

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.ConfirmRequiredFactorEnrollmentUseCase
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import kotlin.uuid.Uuid

/**
 * Confirms required enrollment and consumes the restricted challenge; the caller must sign in again.
 */
internal class RequiredFactorConfirmationHandler(
    private val useCase: ConfirmRequiredFactorEnrollmentUseCase,
    private val secondFactorProblems: SecondFactorProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/required/confirm") {
            val body = call.receive<RequiredConfirmRequestBody>()
            val validation = FieldValidation.Accumulator()
            val pendingId = validation.field("pendingId") { PendingAuthenticationId(Uuid.parse(body.pendingId)) }
            val kind = validation.field("kind") { SecondFactorKind.valueOf(body.kind.uppercase()) }
            validation.field("code") { body.code.takeIf { it.matches(Regex("^[0-9]{6}$")) } }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            val confirmed = useCase.confirm(
                ConfirmRequiredFactorEnrollmentUseCase.Request(pendingId!!, kind!!, body.code),
            )
            if (confirmed) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(Refused(SecondFactorFailure.WrongCode, secondFactorProblems))
            }
        }
    }
}
