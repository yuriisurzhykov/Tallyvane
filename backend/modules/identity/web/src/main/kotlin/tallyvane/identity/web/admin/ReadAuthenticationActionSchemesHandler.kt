package tallyvane.identity.web.admin

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.identity.application.secondfactor.ReadAuthenticationActionSchemesUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused

internal class ReadAuthenticationActionSchemesHandler(
    private val read: ReadAuthenticationActionSchemesUseCase,
    private val current: CurrentPrincipal,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.get("/account/action-proof/options") { readOptions(call) }
    }

    private suspend fun readOptions(call: ApplicationCall) {
        val identity = current.resolve(call) ?: return
        val validation = FieldValidation.Accumulator()
        val action = validation.field("action") {
            AuthenticationAction.valueOf(call.request.queryParameters["action"].orEmpty())
        }
        val errors = validation.errorsOrNull()
        if (errors != null) {
            call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors), validationProblems))
            return
        }
        val schemes = read.read(identity.userId, identity.sessionId, requireNotNull(action))
        call.respond(
            AuthenticationActionProofOptionsBody(
                schemes.map { scheme ->
                    AuthenticationActionProofSchemeBody(
                        scheme.id,
                        scheme.requiredTokens.map(AuthenticationTokenKind::name).sorted(),
                        scheme.assuranceRank,
                    )
                },
            ),
        )
    }
}
