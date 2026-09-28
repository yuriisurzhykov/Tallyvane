package tallyvane.identity.web

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.VerifySecondFactorOutcome
import tallyvane.identity.application.secondfactor.VerifySecondFactorRequest
import tallyvane.identity.application.secondfactor.VerifySecondFactorUseCase
import tallyvane.identity.domain.outcome.SecondFactorOutcome
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.platform.http.Refused
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * `POST /api/v1/auth/mfa/verify` — completes a sign-in
 * [tallyvane.identity.domain.outcome.AuthenticationOutcome.RequiresSecondFactor] left pending,
 * issuing a session on a correct code. Takes no [CurrentPrincipal]: this runs *before* a session
 * exists, the same reason `pendingId` — not a cookie — is how this call names which sign-in it
 * completes.
 */
internal class VerifySecondFactorHandler(
    private val useCase: VerifySecondFactorUseCase,
    private val cookies: SessionCookies,
    private val secondFactorProblems: SecondFactorProblems,
    private val validationProblems: RequestValidationProblems,
    private val accessTtl: Duration,
    private val refreshTtl: Duration,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/mfa/verify") {
            val body = call.receive<VerifyRequestBody>()
            val validation = FieldValidation()
            val pendingId = validation.field("pending_id") { PendingAuthenticationId(Uuid.parse(body.pendingId)) }
            val kind = validation.field("kind") { SecondFactorKind.valueOf(body.kind.uppercase()) }
            val errors = validation.errorsOrNull()
            if (errors != null) {
                call.respond(Refused(RequestValidationFailure(errors), validationProblems))
                return@post
            }

            val request = VerifySecondFactorRequest(pendingId!!, kind!!, body.code)
            when (val outcome = useCase.verify(request)) {
                is VerifySecondFactorOutcome.Issued -> {
                    val tokens = outcome.session.tokens
                    cookies.attach(call, IssuedTokens(tokens.access, accessTtl, tokens.refresh, refreshTtl))
                    call.respond(SignInResponseBody(status = "issued"))
                }

                is VerifySecondFactorOutcome.NotCompleted -> call.respondReason(outcome.reason)
            }
        }
    }

    private suspend fun ApplicationCall.respondReason(reason: SecondFactorOutcome) {
        val failure = when (reason) {
            SecondFactorOutcome.WrongCode -> SecondFactorFailure.WrongCode
            SecondFactorOutcome.Expired -> SecondFactorFailure.Expired
            SecondFactorOutcome.UnknownPending -> SecondFactorFailure.UnknownPending
            SecondFactorOutcome.RateLimited -> SecondFactorFailure.RateLimited
            // Unreachable: VerifySecondFactorUseCase never returns NotCompleted(Completed(...)).
            is SecondFactorOutcome.Completed -> error("VerifySecondFactorOutcome.NotCompleted must never carry Completed")
        }
        respond(Refused(failure, secondFactorProblems))
    }
}
