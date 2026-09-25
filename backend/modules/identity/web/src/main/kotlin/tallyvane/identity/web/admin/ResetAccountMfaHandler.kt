package tallyvane.identity.web.admin

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.ResetAccountMfaUseCase
import tallyvane.identity.domain.user.Email
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused

/**
 * Admin-only reset; authorization is rechecked against the server-side allowlist by the use case.
 */
internal class ResetAccountMfaHandler(
    private val useCase: ResetAccountMfaUseCase,
    private val currentPrincipal: CurrentPrincipal,
    private val policyProblems: AuthenticationPolicyProblems,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/admin/mfa/reset") {
            val principal = currentPrincipal.resolve(call) ?: return@post
            val body = call.receive<ResetMfaRequestBody>()
            val validation = FieldValidation.Accumulator()
            val email = validation.field("email") { Email(body.email) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
                return@post
            }
            when (useCase.reset(ResetAccountMfaUseCase.Request(principal.userId, email!!, body.confirmation))) {
                ResetAccountMfaUseCase.Outcome.RESET -> call.respond(ResetMfaResponseBody("completed"))
                ResetAccountMfaUseCase.Outcome.CONFIRMATION_REQUIRED -> call.respond(
                    Refused(AuthenticationPolicyFailure.Invalid, policyProblems),
                )
                ResetAccountMfaUseCase.Outcome.FORBIDDEN -> call.respond(
                    Refused(AuthenticationPolicyFailure.Forbidden, policyProblems),
                )
                ResetAccountMfaUseCase.Outcome.NOT_FOUND -> call.respond(
                    Refused(AuthenticationPolicyFailure.Invalid, policyProblems),
                )
            }
        }
    }
}
