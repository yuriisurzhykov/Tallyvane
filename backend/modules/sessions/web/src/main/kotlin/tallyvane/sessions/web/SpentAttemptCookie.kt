package tallyvane.sessions.web

import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall

/**
 * Tells the browser to forget the cookie of the sign-in attempt it has just exchanged for a session.
 *
 * `authentication` gives the cookie out and `sessions` takes the attempt it names, so the browser is told
 * here, in the same response. The name is the one `authentication` uses, and the two are not allowed to
 * share code (§4.2), so a rename there must be made here too: the cost of forgetting is only that the
 * browser keeps a cookie that names nothing for the rest of its 15 minutes.
 */
internal class SpentAttemptCookie {
    fun clear(call: ApplicationCall) {
        call.response.cookies.append(
            Cookie(
                name = NAME,
                value = "",
                maxAge = 0,
                path = "/",
                secure = true,
                httpOnly = true,
                extensions = mapOf("SameSite" to "Lax"),
            ),
        )
    }

    override fun toString(): String = "SpentAttemptCookie($NAME)"

    private companion object {
        const val NAME = "__Host-attempt"
    }
}
