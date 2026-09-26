package tallyvane.identity.web.mfa

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.secondfactor.ConfirmRequiredFactorEnrollmentUseCase
import tallyvane.identity.domain.secondfactor.PendingAuthenticationId
import tallyvane.identity.domain.secondfactor.SecondFactorKind
import tallyvane.identity.web.login.SignInResponseBody
import tallyvane.identity.web.routing.AuthHandler
import tallyvane.identity.web.shared.FieldValidation
import tallyvane.identity.web.shared.IssuedTokens
import tallyvane.identity.web.shared.RequestValidationFailure
import tallyvane.identity.web.shared.RequestValidationProblems
import tallyvane.identity.web.shared.SessionCookies
import tallyvane.platform.http.Refused
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * Confirms required enrollment and completes the pending sign-in by issuing session cookies.
 */
internal class RequiredFactorConfirmationHandler(
    private val useCase: ConfirmRequiredFactorEnrollmentUseCase,
    private val cookies: SessionCookies,
    private val secondFactorProblems: SecondFactorProblems,
    private val validationProblems: RequestValidationProblems,
    private val accessTtl: Duration,
    private val refreshTtl: Duration,
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
            when (val outcome = useCase.confirm(
                ConfirmRequiredFactorEnrollmentUseCase.Request(pendingId!!, kind!!, body.code),
            )) {
                is ConfirmRequiredFactorEnrollmentUseCase.Outcome.Issued -> {
                    val tokens = outcome.session.tokens
                    cookies.attach(call, IssuedTokens(tokens.access, accessTtl, tokens.refresh, refreshTtl))
                    call.respond(HttpStatusCode.OK, SignInResponseBody(status = "issued"))
                }

                ConfirmRequiredFactorEnrollmentUseCase.Outcome.NotConfirmed ->
                    call.respond(Refused(SecondFactorFailure.WrongCode, secondFactorProblems))
            }
        }
    }
}
