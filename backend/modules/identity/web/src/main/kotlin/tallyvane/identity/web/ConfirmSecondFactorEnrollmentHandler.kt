package tallyvane.identity.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.ConfirmSecondFactorEnrollmentRequest
import tallyvane.identity.application.secondfactor.ConfirmSecondFactorEnrollmentUseCase
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.http.Refused

/**
 * `POST /api/v1/auth/mfa/confirm` — completes an enrollment `POST /api/v1/auth/mfa/enroll` started,
 * proving the caller actually captured the seed correctly.
 */
internal class ConfirmSecondFactorEnrollmentHandler(
    private val useCase: ConfirmSecondFactorEnrollmentUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val secondFactorProblems: SecondFactorProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/confirm") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            val body = call.receive<ConfirmRequestBody>()
            val validation = FieldValidation()
            val kind = validation.field("kind") { SecondFactorKind.valueOf(body.kind.uppercase()) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure(errors), validationProblems))
                return@post
            }

            val confirmed = useCase.confirm(ConfirmSecondFactorEnrollmentRequest(identity.userId, kind!!, body.code))
            if (confirmed) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                // The use case's own KDoc: a wrong code and an unsupported kind answer identically,
                // so a caller cannot use this endpoint to probe which kinds a deployment supports.
                call.respond(Refused(SecondFactorFailure.WrongCode, secondFactorProblems))
            }
        }
    }
}
