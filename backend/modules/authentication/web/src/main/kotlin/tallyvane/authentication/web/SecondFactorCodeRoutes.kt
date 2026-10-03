package tallyvane.authentication.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.authentication.application.Verification
import tallyvane.authentication.application.VerifySecondFactorUseCase
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule

/**
 * Answering a second step of a sign-in, or of a confirmation of a dangerous act (ADR-093).
 *
 * ```
 * POST /api/v1/second-factor-codes   {kind: totp | recovery_code, code}
 * ```
 *
 * Public: the browser's `__Host-attempt` cookie is what says which attempt this answers. Answers `204`
 * for a code from the authenticator and `200` with the number of recovery codes left for a recovery code.
 * What it proves is only recorded on the attempt; redeeming it for a session or taking it as a fresh
 * proof is the next request.
 *
 * A wrong answer is `422` and a running pause is `429`, each with `Retry-After`; an attempt that is over
 * is `410`.
 */
internal class SecondFactorCodeRoutes(
    private val verify: VerifySecondFactorUseCase,
    private val cookie: AttemptCookie,
    private val problems: SecondFactorCodeProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/second-factor-codes")

    override val access: Access = Access.Public

    override fun install(route: Route) {
        route.post {
            val answer = call.receive<SecondFactorCode>()
            when (val outcome = verify.verify(cookie.secretIn(call), answer.submission())) {
                is Verification.Verified -> {
                    val remaining = outcome.reportTo(Telling())
                    if (remaining == null) call.respond(HttpStatusCode.NoContent) else call.respond(remaining)
                }
                is Verification.Failed -> {
                    outcome.retryAfter { wait -> RetryAfter().tell(call, wait) }
                    call.respond(Refused(outcome, problems))
                }
            }
        }
    }

    override fun toString(): String = "SecondFactorCodeRoutes"

    private class Telling : Verification.Verified.Report<SecondFactorCodeAccepted?> {
        override fun totp(): SecondFactorCodeAccepted? = null

        override fun recovery(remaining: Int): SecondFactorCodeAccepted = SecondFactorCodeAccepted(remaining)
    }
}
