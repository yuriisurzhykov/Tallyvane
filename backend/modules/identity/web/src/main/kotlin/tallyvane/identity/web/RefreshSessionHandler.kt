package tallyvane.identity.web

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import tallyvane.identity.application.session.RefreshSessionOutcome
import tallyvane.identity.application.session.RefreshSessionUseCase
import tallyvane.identity.domain.token.TokenValue
import tallyvane.platform.http.Refused
import kotlin.time.Duration

/**
 * `POST /api/v1/auth/refresh` — redeems the `refresh` cookie [SessionCookies.readRefresh] reads
 * for a fresh access/refresh pair, per RFC 9700 §4.14.2's rotation.
 */
internal class RefreshSessionHandler(
    private val useCase: RefreshSessionUseCase,
    private val cookies: SessionCookies,
    private val problems: SessionProblems,
    private val accessTtl: Duration,
    private val refreshTtl: Duration,
) : AuthHandler {
    override fun install(route: Route) {
        route.post("/refresh") {
            val raw = cookies.readRefresh(call)
            val presented = raw?.let { runCatching { TokenValue(it) }.getOrNull() }
            if (presented == null) {
                call.respond(Refused(SessionFailure.RefreshInvalid, problems))
                return@post
            }

            when (val outcome = useCase.refresh(presented)) {
                is RefreshSessionOutcome.Issued -> {
                    cookies.attach(call, IssuedTokens(outcome.tokens.access, accessTtl, outcome.tokens.refresh, refreshTtl))
                    call.respond(SignInResponseBody(status = "issued"))
                }

                RefreshSessionOutcome.Invalid -> call.respond(Refused(SessionFailure.RefreshInvalid, problems))
                RefreshSessionOutcome.ReuseDetected -> call.respond(Refused(SessionFailure.RefreshReused, problems))
            }
        }
    }
}
