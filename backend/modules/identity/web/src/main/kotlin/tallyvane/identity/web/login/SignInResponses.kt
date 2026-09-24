package tallyvane.identity.web.login

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.web.shared.IssuedTokens
import tallyvane.identity.web.shared.SessionCookies
import tallyvane.platform.http.Refused
import tallyvane.platform.http.problems.Problems
import kotlin.time.Duration

/**
 * The one place a [SignInOutcome] becomes an HTTP answer — shared by every primary sign-in method
 * (password, both Google methods), since all three hand back the identical outcome shape and none
 * of them should re-derive this mapping on its own.
 */
internal interface SignInResponses {
    fun attachIssued(call: ApplicationCall, outcome: SignInOutcome.Issued)
    suspend fun respond(call: ApplicationCall, outcome: SignInOutcome, problems: Problems<AuthenticationFailure>)

    class Writer(
        private val cookies: SessionCookies,
        private val accessTtl: Duration,
        private val refreshTtl: Duration,
    ) : SignInResponses {
        override fun attachIssued(call: ApplicationCall, outcome: SignInOutcome.Issued) {
            cookies.attach(
                call,
                IssuedTokens(outcome.session.tokens.access, accessTtl, outcome.session.tokens.refresh, refreshTtl),
            )
        }

        override suspend fun respond(
            call: ApplicationCall,
            outcome: SignInOutcome,
            problems: Problems<AuthenticationFailure>,
        ) {
            when (outcome) {
                is SignInOutcome.Issued -> {
                    attachIssued(call, outcome)
                    call.respond(HttpStatusCode.OK, SignInResponseBody(status = STATUS_ISSUED))
                }

                is SignInOutcome.NotIssued -> respondReason(call, outcome.reason, problems)
            }
        }

        private suspend fun respondReason(
            call: ApplicationCall,
            reason: AuthenticationOutcome,
            problems: Problems<AuthenticationFailure>,
        ) {
            when (reason) {
                is AuthenticationOutcome.RequiresSecondFactor -> call.respond(
                    HttpStatusCode.OK,
                    SignInResponseBody(
                        status = STATUS_REQUIRES_SECOND_FACTOR,
                        pendingId = reason.pendingId.value.toString(),
                        availableMethods = reason.availableMethods.map { it.name },
                    ),
                )

                is AuthenticationOutcome.InvalidCredential    -> call.respond(
                    Refused(
                        AuthenticationFailure.InvalidCredential,
                        problems,
                    ),
                )

                is AuthenticationOutcome.AccountDisabled      -> call.respond(
                    Refused(
                        AuthenticationFailure.AccountDisabled,
                        problems,
                    ),
                )

                is AuthenticationOutcome.RateLimited          -> call.respond(
                    Refused(
                        AuthenticationFailure.RateLimited,
                        problems,
                    ),
                )
                // Unreachable in practice: SessionIssuer.complete never returns NotIssued(Success(...)),
                // per AuthenticationCompleter's own logic. Handled anyway because AuthenticationOutcome
                // is a closed type and this `when` must be exhaustive.
                is AuthenticationOutcome.Success              -> error("SignInOutcome.NotIssued must never carry AuthenticationOutcome.Success")
            }
        }

        private companion object {
            const val STATUS_ISSUED = "issued"
            const val STATUS_REQUIRES_SECOND_FACTOR = "requires_second_factor"
        }
    }
}
