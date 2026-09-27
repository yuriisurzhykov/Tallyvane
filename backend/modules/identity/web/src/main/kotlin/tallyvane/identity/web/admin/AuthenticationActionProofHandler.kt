package tallyvane.identity.web.admin

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.AuthorizeAuthenticationActionUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.identity.web.login.AuthenticationFailure
import tallyvane.identity.web.login.AuthenticationProblems
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.CurrentPrincipal
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.platform.http.Refused
import kotlin.uuid.Uuid

internal class AuthenticationActionProofHandler(
    private val authorize: AuthorizeAuthenticationActionUseCase,
    private val current: CurrentPrincipal,
    private val validationProblems: RequestValidationProblems,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/account/action-proof") { authorizeAction(call) }
    }

    private suspend fun authorizeAction(call: ApplicationCall) {
        val identity = current.resolve(call) ?: return
        val body = call.receive<AuthenticationActionProofBody>()
        val validation = FieldValidation.Accumulator()
        val action = validation.field("action") { AuthenticationAction.valueOf(body.action) }
        val tokens = body.tokens.map { presented ->
            val kind = validation.field("tokens.kind") { AuthenticationTokenKind.valueOf(presented.kind) }
            val challengeId = presented.challengeId?.let { value ->
                validation.field("tokens.challenge_id") { Uuid.parse(value) }
            }
            AuthorizeAuthenticationActionUseCase.PresentedToken(
                kind = kind ?: AuthenticationTokenKind.PASSWORD,
                value = presented.value,
                challengeId = challengeId,
                codeVerifier = presented.codeVerifier,
                redirectUri = presented.redirectUri,
            )
        }
        val errors = validation.errorsOrNull()
        if (errors != null || body.tokens.isEmpty()) {
            call.respond(Refused(RequestValidationFailure.FieldsInvalid(errors.orEmpty()), validationProblems))
            return
        }
        respondToAuthorization(
            call,
            AuthorizeAuthenticationActionUseCase.Request(
                identity.userId,
                identity.sessionId,
                requireNotNull(action),
                tokens,
            ),
        )
    }

    private suspend fun respondToAuthorization(
        call: ApplicationCall,
        request: AuthorizeAuthenticationActionUseCase.Request,
    ) {
        when (val result = authorize.authorize(request)) {
            is AuthorizeAuthenticationActionUseCase.Result.Authorized -> call.respond(
                AuthenticationActionProofResponseBody(
                    result.proof.raw,
                    result.schemeId,
                    result.assuranceRank,
                    result.expiresAt.toString(),
                ),
            )
            AuthorizeAuthenticationActionUseCase.Result.Refused -> call.respond(
                Refused(AuthenticationFailure.InvalidCredential, AuthenticationProblems()),
            )
        }
    }
}
