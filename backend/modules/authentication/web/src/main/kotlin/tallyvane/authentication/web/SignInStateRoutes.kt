package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.authentication.application.ShowSignInUseCase
import tallyvane.authentication.application.SignInShown
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.Refused
import tallyvane.platform.http.RouteModule
import kotlin.time.Duration

/**
 * Where a sign-in, or a confirmation of a dangerous act, stands (ADR-093).
 *
 * ```
 * GET /api/v1/sign-in   what the attempt waits for, how long to wait, or that it is over
 * ```
 *
 * Public, like the routes that begin an attempt: the browser's `__Host-attempt` cookie is the only thing
 * it holds. Never cached, because the answer changes with every request that answers the attempt.
 */
internal class SignInStateRoutes(
    private val show: ShowSignInUseCase,
    private val cookie: AttemptCookie,
    private val problems: SignInStateProblems,
) : RouteModule {
    override val basePath: BasePath = BasePath("/sign-in")

    override val access: Access = Access.Public

    override fun install(route: Route) {
        route.get {
            call.response.header(HttpHeaders.CacheControl, "no-store")
            when (val outcome = show.show(cookie.secretIn(call))) {
                is SignInShown.Shown -> call.respond(outcome.reportTo(Telling()))
                is SignInShown.Failed -> call.respond(Refused(outcome, problems))
            }
        }
    }

    override fun toString(): String = "SignInStateRoutes"

    private class Telling : SignInShown.Shown.Report<SignInState> {
        override fun awaiting(totp: Boolean, recoveryCode: Boolean): SignInState =
            SignInState("awaiting", wanted(totp, recoveryCode))

        override fun paused(totp: Boolean, recoveryCode: Boolean, wait: Duration): SignInState =
            SignInState("paused", wanted(totp, recoveryCode), RetryAfter().seconds(wait))

        override fun complete(): SignInState = SignInState("complete", emptyList())

        override fun restricted(): SignInState = SignInState("restricted", emptyList())

        override fun exhausted(): SignInState = SignInState("exhausted", emptyList())

        override fun expired(): SignInState = SignInState("expired", emptyList())

        private fun wanted(totp: Boolean, recoveryCode: Boolean): List<String> =
            listOfNotNull("totp".takeIf { totp }, "recovery_code".takeIf { recoveryCode })
    }
}
