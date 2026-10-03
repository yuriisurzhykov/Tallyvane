package tallyvane.authentication.web

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import tallyvane.authentication.application.ContinueWithGoogleUseCase
import tallyvane.authentication.application.GoogleReply
import tallyvane.authentication.application.GoogleReturn
import tallyvane.authentication.application.TurnBack
import tallyvane.platform.http.Access
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Secret

/**
 * Where Google sends the browser back to.
 *
 * ```
 * GET /api/v1/google-return?code=…&state=…   a person comes back from Google
 * ```
 *
 * Reached by a redirect, so it never answers with an error document: every outcome is another
 * redirect, to a page of the application. A person with an account goes on to `/login/continue`, a new
 * person to `/welcome` with a new cookie, one confirming a dangerous act to `/step-up/continue`, and
 * anyone else back to `/login` with a reason.
 *
 * This is the one address registered with Google as the redirect URI.
 */
internal class GoogleReturnRoutes(
    private val continueWith: ContinueWithGoogleUseCase,
    private val cookie: AttemptCookie,
    private val pages: ReturnPages,
) : RouteModule {
    override val basePath: BasePath = BasePath("/google-return")

    override val access: Access = Access.Public

    override fun install(route: Route) {
        route.get {
            val code = call.request.queryParameters["code"]
            val state = call.request.queryParameters["state"]
            val reply = if (code != null && state != null) GoogleReply.Granted(code, state) else GoogleReply.Declined()
            val returned = continueWith.continueWith(cookie.secretIn(call), reply)
            call.respondRedirect(returned.reportTo(Landing(call, cookie, pages)))
        }
    }

    override fun toString(): String = "GoogleReturnRoutes"

    /**
     * Where a return from Google ends up, and what the cookie does on the way.
     */
    private class Landing(
        private val call: ApplicationCall,
        private val cookie: AttemptCookie,
        private val pages: ReturnPages,
    ) : GoogleReturn.Report<String> {
        override fun verified(): String = pages.afterVerified()

        override fun steppedUp(): String = pages.afterSteppedUp()

        override fun registering(attempt: Secret): String {
            cookie.give(call, attempt)
            return pages.afterRegistering()
        }

        override fun turnedBack(reason: TurnBack): String {
            cookie.clear(call)
            return pages.afterTurnedBack(reason)
        }
    }
}
