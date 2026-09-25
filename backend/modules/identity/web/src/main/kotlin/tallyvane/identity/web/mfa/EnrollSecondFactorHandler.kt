package tallyvane.identity.web.mfa

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.EnrollSecondFactorRequest
import tallyvane.identity.application.secondfactor.EnrollSecondFactorUseCase
import tallyvane.identity.application.secondfactor.ReadSecondFactorStatusUseCase
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
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
    private val status: ReadSecondFactorStatusUseCase,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/enroll") {
            val identity = currentPrincipal.resolve(call) ?: return@post
            if (!status.read(identity.userId, identity.sessionId).recentlyAuthenticated) {
                call.respond(Refused(SecondFactorFailure.ReauthenticationRequired, secondFactorProblems))
                return@post
            }
            val body = call.receive<EnrollRequestBody>()
            val validation = FieldValidation.Accumulator()
            val kind = validation.field("kind") { SecondFactorKind.valueOf(body.kind.uppercase()) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
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
