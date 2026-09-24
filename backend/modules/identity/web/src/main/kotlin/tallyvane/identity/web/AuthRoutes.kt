package tallyvane.identity.web

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.response.respond
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import tallyvane.platform.http.BasePath
import tallyvane.platform.http.RouteModule
import java.security.SecureRandom
import java.util.Base64

/**
 * Every address under `/api/v1/auth` `identity` answers, mounted at the one base path
 * [tallyvane.platform.http.Api] lets a module own. [handlers] is what actually does the work —
 * see [AuthHandler]'s own KDoc for why one [RouteModule] holds many of them instead of `identity`
 * needing one [BasePath] per action.
 */
internal class AuthRoutes(
    private val handlers: List<AuthHandler>,
    private val secure: Boolean,
    private val googleEnabled: Boolean = false,
    private val googleOAuth: GoogleOAuthHandler? = null,
    private val registrationEmailVerification: VerifyRegistrationEmailHandler? = null,
) : RouteModule {
    override val basePath: BasePath = BasePath("/auth")

    override fun install(route: Route) {
        route.get { call.respond(mapOf("status" to "available")) }
        route.get("/csrf") {
            call.response.headers.append("Cache-Control", "no-store")
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            call.response.cookies.append(
                Cookie("csrf_token", token, path = "/", secure = secure, httpOnly = true, extensions = mapOf("SameSite" to "Strict")),
            )
            call.respond(mapOf("token" to token))
        }
        route.get("/providers") { call.respond(mapOf("google" to googleEnabled)) }
        googleOAuth?.let { oauth ->
            route.get("/google/oauth/start") { oauth.start(call) }
            route.get("/google/callback") { oauth.callback(call) }
        }
        registrationEmailVerification?.let { verification ->
            route.post("/register/email/verify") { verification.handle(call) }
        }
        handlers.forEach { it.install(route) }
    }
}
