package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.CodesRegenerated
import tallyvane.authentication.application.RegenerateRecoveryCodesUseCase
import tallyvane.identity.contract.AccountId
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.Requester
import tallyvane.platform.http.RouteModule
import tallyvane.platform.http.SecretAnswer

/**
 * Asking for ten new recovery codes (ADR-082, ADR-093).
 *
 * ```
 * POST /api/v1/recovery-codes   replaces every code the person had; the new ones, told once
 * ```
 *
 * A dangerous act, so the edge asks for a recent proof of who the person is first (ADR-092). The answer
 * carries the codes, so it is never stored for a repeat of its `Idempotency-Key`, and never cached.
 */
internal class RecoveryCodeRoutes(
    private val regenerate: RegenerateRecoveryCodesUseCase,
    private val problems: RecoveryCodeProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/recovery-codes")

    override val access: Access = Access.SignedFresh

    override fun install(route: Route) {
        route.post {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (val outcome = regenerate.regenerate(AccountId(Requester(call).account()))) {
                is CodesRegenerated.Regenerated -> {
                    SecretAnswer(call).withheldFromReplay()
                    call.respond(issuedBy(outcome))
                }
                is CodesRegenerated.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    private fun issuedBy(outcome: CodesRegenerated.Regenerated): RecoveryCodesIssued {
        val told = mutableListOf<RecoveryCodesIssued>()
        outcome.writeTo { codes -> told += RecoveryCodesIssued(codes.map { it.revealed() }) }
        return told.single()
    }

    override fun toString(): String = "RecoveryCodeRoutes"
}
