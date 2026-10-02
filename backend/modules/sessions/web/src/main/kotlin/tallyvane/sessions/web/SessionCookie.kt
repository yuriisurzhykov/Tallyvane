package tallyvane.sessions.web

import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import tallyvane.platform.kernel.Secret
import kotlin.time.Duration

/**
 * The cookie that carries a browser's session from one request to the next (ADR-080).
 *
 * Its value is the random secret the session is kept under; the database holds only a digest of it.
 * `__Host-` makes the browser refuse the cookie unless it is `Secure`, has `Path=/` and names no `Domain`,
 * so a sibling subdomain cannot plant one. `HttpOnly` keeps it from scripts. `SameSite=Lax` is the first of
 * two defences against a forged request; the second is the origin check at the edge.
 */
internal class SessionCookie {
    /**
     * The secret the browser sent, or null when it sent none.
     */
    fun secretIn(call: ApplicationCall): Secret? = call.request.cookies[NAME]?.takeIf { it.isNotBlank() }?.let(::Secret)

    /**
     * Gives the browser [secret] to send back, to forget after [lasting] at the most. The session
     * decides when it is really over; the cookie only has to outlast it.
     */
    fun give(call: ApplicationCall, secret: Secret, lasting: Duration) {
        call.response.cookies.append(cookieOf(secret.revealed(), lasting.inWholeSeconds.toInt()))
    }

    /**
     * Tells the browser to forget its session.
     */
    fun clear(call: ApplicationCall) {
        call.response.cookies.append(cookieOf("", 0))
    }

    private fun cookieOf(value: String, maxAge: Int) = Cookie(
        name = NAME,
        value = value,
        maxAge = maxAge,
        path = "/",
        secure = true,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Lax"),
    )

    override fun toString(): String = "SessionCookie($NAME)"

    private companion object {
        const val NAME = "__Host-session"
    }
}
