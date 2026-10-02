package tallyvane.authentication.web

import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import tallyvane.platform.kernel.Secret

/**
 * The cookie that carries a browser's sign-in attempt from one request to the next (slice 3, fork 2).
 *
 * Its value is the random secret the attempt is kept under; the database holds only a digest of it.
 * `__Host-` makes the browser refuse the cookie unless it is `Secure`, has `Path=/` and names no
 * `Domain`, so a sibling subdomain cannot plant one. `HttpOnly` keeps it from scripts. `SameSite=Lax`
 * still sends it on the top-level navigation that brings the person back from Google, which `Strict`
 * would not.
 */
internal class AttemptCookie {
    /**
     * The secret the browser sent, or null when it sent none.
     */
    fun secretIn(call: ApplicationCall): Secret? = call.request.cookies[NAME]?.takeIf { it.isNotBlank() }?.let(::Secret)

    /**
     * Gives the browser [secret] to send back.
     */
    fun give(call: ApplicationCall, secret: Secret) {
        call.response.cookies.append(cookieOf(secret.revealed(), MAX_AGE_SECONDS))
    }

    /**
     * Tells the browser to forget its attempt.
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

    override fun toString(): String = "AttemptCookie($NAME)"

    private companion object {
        const val NAME = "__Host-attempt"

        /**
         * Longer than any attempt may live (15 minutes, ADR-078): the attempt decides when it is over,
         * the cookie only has to outlast it.
         */
        const val MAX_AGE_SECONDS = 900
    }
}
