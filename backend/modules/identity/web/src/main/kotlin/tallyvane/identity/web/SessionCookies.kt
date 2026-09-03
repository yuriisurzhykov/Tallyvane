package tallyvane.identity.web

import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import tallyvane.identity.domain.token.TokenValue
import tallyvane.platform.http.RequestPrincipal
import kotlin.time.Duration

/**
 * Writes the two cookies the Token Handler / BFF pattern calls for: the access token, readable by
 * every route through [RequestPrincipal.COOKIE_NAME], and the refresh token, scoped to the one
 * path that ever needs it so it is not attached to every other request this account holder makes.
 *
 * Neither cookie's raw value ever reaches a response body — see [tallyvane.identity.application.IssuedSession]'s
 * own KDoc for why that is true one layer down already; this class is where the design's own
 * "opaque `HttpOnly` cookie, never a token in a body" promise is actually kept.
 *
 * @param secure Whether both cookies carry `Secure` — `false` in development, where this process
 * answers over plain `http://localhost` and Postman (unlike a browser) does not special-case
 * `localhost` for a `Secure` cookie at all: measured, not assumed, before choosing this to be a
 * process setting rather than a constant.
 */
internal class SessionCookies(private val secure: Boolean) {
    fun attach(call: ApplicationCall, tokens: IssuedTokens) {
        call.response.cookies.append(accessCookie(tokens.access, tokens.accessTtl))
        call.response.cookies.append(refreshCookie(tokens.refresh, tokens.refreshTtl))
    }

    /**
     * The raw value `/auth/refresh` reads — `null` if the browser sent no `refresh` cookie at all,
     * which [RefreshSessionOutcome.Invalid][tallyvane.identity.application.session.RefreshSessionOutcome.Invalid]
     * already covers once this method's caller turns the absence into that same outcome.
     */
    fun readRefresh(call: ApplicationCall): String? = call.request.cookies[REFRESH_COOKIE]

    /**
     * Sent on the `/auth/refresh` path only — [attach] just replaced the access cookie with a
     * fresh pair anyway, so a refresh's own response only has a refresh token to attach, never a
     * reason to touch the access one twice.
     */
    fun attachRefreshOnly(call: ApplicationCall, refresh: TokenValue, refreshTtl: Duration) {
        call.response.cookies.append(refreshCookie(refresh, refreshTtl))
    }

    /**
     * Expires both cookies immediately — a sign-out clearing what a sign-in attached.
     */
    fun clear(call: ApplicationCall) {
        call.response.cookies.append(expired(ACCESS_COOKIE, ACCESS_PATH))
        call.response.cookies.append(expired(REFRESH_COOKIE, REFRESH_PATH))
    }

    private fun accessCookie(token: TokenValue, ttl: Duration): Cookie = cookie(ACCESS_COOKIE, token.raw, ACCESS_PATH, ttl)

    private fun refreshCookie(token: TokenValue, ttl: Duration): Cookie = cookie(REFRESH_COOKIE, token.raw, REFRESH_PATH, ttl)

    private fun cookie(name: String, value: String, path: String, ttl: Duration): Cookie = Cookie(
        name = name,
        value = value,
        maxAge = ttl.inWholeSeconds.toInt(),
        path = path,
        secure = secure,
        httpOnly = true,
        extensions = SAME_SITE_LAX,
    )

    private fun expired(name: String, path: String): Cookie = Cookie(
        name = name,
        value = "",
        maxAge = 0,
        path = path,
        secure = secure,
        httpOnly = true,
        extensions = SAME_SITE_LAX,
    )

    private companion object {
        const val ACCESS_COOKIE = RequestPrincipal.COOKIE_NAME
        const val ACCESS_PATH = "/"

        const val REFRESH_COOKIE = "refresh"

        /**
         * Scoped to `identity`'s own base path rather than site-wide: the refresh token is the
         * longer-lived, more sensitive of the two, and there is no reason for the browser to send
         * it on every request the way the access token has to be.
         */
        const val REFRESH_PATH = "/api/v1/auth/refresh"

        val SAME_SITE_LAX = mapOf("SameSite" to "Lax")
    }
}
