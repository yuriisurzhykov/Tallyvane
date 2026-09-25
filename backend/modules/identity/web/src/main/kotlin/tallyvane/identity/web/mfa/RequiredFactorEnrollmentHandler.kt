package tallyvane.identity.web.mfa

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.BeginRequiredFactorEnrollmentUseCase
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import kotlin.uuid.Uuid

/**
 * Starts enrollment with the expiring capability returned when policy requires MFA enrollment.
 */
internal class RequiredFactorEnrollmentHandler(
    private val useCase: BeginRequiredFactorEnrollmentUseCase,
    private val secondFactorProblems: SecondFactorProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/required/enroll") {
            val body = call.receive<RequiredEnrollRequestBody>()
            val validation = FieldValidation.Accumulator()
            val pendingId = validation.field("pendingId") { PendingAuthenticationId(Uuid.parse(body.pendingId)) }
            val kind = validation.field("kind") { SecondFactorKind.valueOf(body.kind.uppercase()) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            val payload = useCase.begin(BeginRequiredFactorEnrollmentUseCase.Request(pendingId!!, kind!!))
            if (payload == null) {
                call.respond(Refused(SecondFactorFailure.UnknownPending, secondFactorProblems))
            } else {
                call.respond(EnrollResponseBody(payload))
            }
        }
    }
}
