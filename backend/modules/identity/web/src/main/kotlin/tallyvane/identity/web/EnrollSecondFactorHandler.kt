package tallyvane.identity.web

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.EnrollSecondFactorRequest
import tallyvane.identity.application.secondfactor.EnrollSecondFactorUseCase
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.http.Refused

/**
 * `POST /api/v1/auth/mfa/enroll` — starts enrolling the caller's own account in one second factor,
 * per ADR-053 distinct from confirming it (`POST /api/v1/auth/mfa/confirm`).
 */
internal class EnrollSecondFactorHandler(
    private val useCase: EnrollSecondFactorUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val secondFactorProblems: SecondFactorProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/enroll") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            val body = call.receive<EnrollRequestBody>()
            val validation = FieldValidation()
            val kind = validation.field("kind") { SecondFactorKind.valueOf(body.kind.uppercase()) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure(errors), validationProblems))
                return@post
            }

            val payload = useCase.enroll(EnrollSecondFactorRequest(identity.userId, kind!!))
            if (payload == null) {
                call.respond(Refused(SecondFactorFailure.UnsupportedMethod, secondFactorProblems))
            } else {
                call.respond(EnrollResponseBody(payload))
            }
        }
    }
}
