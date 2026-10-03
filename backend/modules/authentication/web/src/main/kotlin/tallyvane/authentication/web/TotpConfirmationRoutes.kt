package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.ConfirmTotpUseCase
import tallyvane.authentication.application.TotpConfirmed
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.SecretAnswer

/**
 * Typing the first code, which turns TOTP on and tells the recovery codes (ADR-093).
 *
 * ```
 * POST /api/v1/totp-confirmations   {code}; the ten recovery codes, told once
 * ```
 *
 * Closed to everyone but a signed-in person; it needs nothing fresher, since the code is itself the
 * proof that the person holds the seed. The answer carries the recovery codes, so it is never stored for
 * a repeat of its `Idempotency-Key`, and never cached.
 */
internal class TotpConfirmationRoutes(
    private val confirm: ConfirmTotpUseCase,
    private val problems: TotpConfirmationProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/totp-confirmations")

    override fun install(route: Route) {
        route.post {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val first = call.receive<FirstCode>()
            when (val outcome = confirm.confirm(AccountId(Requester(call).account()), first.code)) {
                is TotpConfirmed.Confirmed -> {
                    SecretAnswer(call).withheldFromReplay()
                    call.respond(issuedBy(outcome))
                }
                is TotpConfirmed.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun issuedBy(outcome: TotpConfirmed.Confirmed): RecoveryCodesIssued {
        val told = mutableListOf<RecoveryCodesIssued>()
        outcome.writeTo { codes -> told += RecoveryCodesIssued(codes.map { it.revealed() }) }
        return told.single()
    }

    override fun toString(): String = "TotpConfirmationRoutes"
}
