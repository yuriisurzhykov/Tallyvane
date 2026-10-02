package tallyvane.sessions.web

import io.ktor.server.application.ApplicationCall
import tallyvane.platform.kernel.Secret

/**
 * Reads the secret of the sign-in attempt out of the cookie `authentication` gave the browser.
 *
 * See [SpentAttemptCookie] for why the name is repeated here.
 */
internal class AttemptSecret {
    fun of(call: ApplicationCall): Secret? = call.request.cookies[NAME]?.takeIf { it.isNotBlank() }?.let(::Secret)

    override fun toString(): String = "AttemptSecret($NAME)"

    private companion object {
        const val NAME = "__Host-attempt"
    }
}
