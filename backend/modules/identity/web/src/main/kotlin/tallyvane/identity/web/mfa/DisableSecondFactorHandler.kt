package tallyvane.identity.web.mfa

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.DisableSecondFactorUseCase
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.platform.http.Refused

internal class DisableSecondFactorHandler(
    private val disable: DisableSecondFactorUseCase,
    private val current: CurrentPrincipal,
    private val problems: SecondFactorProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/disable") {
            val identity = current.resolve(call) ?: return@post
            val body = call.receive<DisableSecondFactorBody>()
            val kind = runCatching { SecondFactorKind.valueOf(body.kind) }.getOrNull()
            if (kind == null) {
                call.respond(Refused(SecondFactorFailure.UnsupportedMethod, problems))
                return@post
            }
            when (
                disable.disable(
                    DisableSecondFactorUseCase.Request(identity.userId, identity.sessionId, kind, body.confirmed),
                )
            ) {
                DisableSecondFactorUseCase.Outcome.DISABLED -> call.respond(HttpStatusCode.NoContent)
                DisableSecondFactorUseCase.Outcome.NOT_ENROLLED ->
                    call.respond(Refused(SecondFactorFailure.NotEnrolled, problems))
                DisableSecondFactorUseCase.Outcome.REAUTHENTICATION_REQUIRED ->
                    call.respond(Refused(SecondFactorFailure.ReauthenticationRequired, problems))
                DisableSecondFactorUseCase.Outcome.REQUIRED_BY_POLICY ->
                    call.respond(Refused(SecondFactorFailure.RequiredByPolicy, problems))
                DisableSecondFactorUseCase.Outcome.CONFIRMATION_REQUIRED ->
                    call.respond(Refused(SecondFactorFailure.ConfirmationRequired, problems))
            }
        }
    }
}
