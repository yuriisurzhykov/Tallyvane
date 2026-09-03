package tallyvane.identity.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.platform.http.Refused
import tallyvane.platform.http.problems.Problems
import kotlin.time.Duration

/**
 * The one place a [SignInOutcome] becomes an HTTP answer — shared by every primary sign-in method
 * (password, both Google methods), since all three hand back the identical outcome shape and none
 * of them should re-derive this mapping on its own.
 */
internal class SignInResponses(private val cookies: SessionCookies, private val accessTtl: Duration, private val refreshTtl: Duration) {
    suspend fun respond(call: ApplicationCall, outcome: SignInOutcome, problems: Problems<AuthenticationFailure>) {
        when (outcome) {
            is SignInOutcome.Issued -> {
                cookies.attach(
                    call,
                    IssuedTokens(outcome.session.tokens.access, accessTtl, outcome.session.tokens.refresh, refreshTtl),
                )
                call.respond(HttpStatusCode.OK, SignInResponseBody(status = STATUS_ISSUED))
            }

            is SignInOutcome.NotIssued -> respondReason(call, outcome.reason, problems)
        }
    }

    private suspend fun respondReason(call: ApplicationCall, reason: AuthenticationOutcome, problems: Problems<AuthenticationFailure>) {
        when (reason) {
            is AuthenticationOutcome.RequiresSecondFactor -> call.respond(
                HttpStatusCode.OK,
                SignInResponseBody(
                    status = STATUS_REQUIRES_SECOND_FACTOR,
                    pendingId = reason.pendingId.value.toString(),
                    availableMethods = reason.availableMethods.map { it.name },
                ),
            )

            AuthenticationOutcome.InvalidCredential -> call.respond(Refused(AuthenticationFailure.InvalidCredential, problems))
            AuthenticationOutcome.AccountDisabled -> call.respond(Refused(AuthenticationFailure.AccountDisabled, problems))
            AuthenticationOutcome.RateLimited -> call.respond(Refused(AuthenticationFailure.RateLimited, problems))
            // Unreachable in practice: SessionIssuer.complete never returns NotIssued(Success(...)),
            // per AuthenticationCompleter's own logic. Handled anyway because AuthenticationOutcome
            // is a closed type and this `when` must be exhaustive.
            is AuthenticationOutcome.Success -> error("SignInOutcome.NotIssued must never carry AuthenticationOutcome.Success")
        }
    }

    private companion object {
        const val STATUS_ISSUED = "issued"
        const val STATUS_REQUIRES_SECOND_FACTOR = "requires_second_factor"
    }
}
