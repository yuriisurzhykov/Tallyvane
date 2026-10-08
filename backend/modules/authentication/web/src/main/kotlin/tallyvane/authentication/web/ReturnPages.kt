package tallyvane.authentication.web

import tallyvane.authentication.application.TurnBack
import tallyvane.platform.http.Surfaces
import tallyvane.platform.kernel.Surface

/**
 * Where the browser lands when Google sends the person back. Only pages of the application itself,
 * all fixed here: no address ever comes from the request, so the callback cannot be made to redirect
 * somewhere else (an open redirect).
 *
 * The page is on the origin of the door the person came back through (ADR-097): the host-only
 * cookies of one door are of no use on the other.
 */
internal class ReturnPages(private val surfaces: Surfaces) {
    /**
     * Signed in as far as Google goes; the page decides what is still wanted.
     */
    fun afterVerified(surface: Surface): String = "${origin(surface)}/login/continue"

    /**
     * A signed-in person who came back from Google to confirm a dangerous act.
     */
    fun afterSteppedUp(surface: Surface): String = "${origin(surface)}/step-up/continue"

    /**
     * A new person: the welcome form.
     */
    fun afterRegistering(surface: Surface): String = "${origin(surface)}/welcome"

    /**
     * Back to the sign-in page, saying why in a word the page knows.
     */
    fun afterTurnedBack(surface: Surface, reason: TurnBack): String =
        "${origin(surface)}/login?problem=${codeOf(reason)}"

    private fun origin(surface: Surface): String = surfaces.originOf(surface)

    private fun codeOf(reason: TurnBack): String = when (reason) {
        TurnBack.Restart -> "restart"
        TurnBack.Expired -> "expired"
        TurnBack.Cancelled -> "cancelled"
        TurnBack.Refused -> "refused"
        TurnBack.EmailUnverified -> "email-unverified"
        TurnBack.Unavailable -> "unavailable"
    }

    override fun toString(): String = "ReturnPages($surfaces)"
}
