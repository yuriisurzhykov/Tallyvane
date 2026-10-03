package tallyvane.authentication.web

import tallyvane.authentication.application.TurnBack

/**
 * Where the browser lands when Google sends the person back. Only pages of the application itself,
 * all fixed here: no address ever comes from the request, so the callback cannot be made to redirect
 * somewhere else (an open redirect).
 *
 * @param origin The application's own origin, such as `https://app.tallyvane.com`.
 */
internal class ReturnPages(private val origin: String) {
    /**
     * Signed in as far as Google goes; the page decides what is still wanted.
     */
    fun afterVerified(): String = "$origin/login/continue"

    /**
     * A signed-in person who came back from Google to confirm a dangerous act.
     */
    fun afterSteppedUp(): String = "$origin/step-up/continue"

    /**
     * A new person: the welcome form.
     */
    fun afterRegistering(): String = "$origin/welcome"

    /**
     * Back to the sign-in page, saying why in a word the page knows.
     */
    fun afterTurnedBack(reason: TurnBack): String = "$origin/login?problem=${codeOf(reason)}"

    private fun codeOf(reason: TurnBack): String = when (reason) {
        TurnBack.Restart -> "restart"
        TurnBack.Expired -> "expired"
        TurnBack.Cancelled -> "cancelled"
        TurnBack.Refused -> "refused"
        TurnBack.EmailUnverified -> "email-unverified"
        TurnBack.Unavailable -> "unavailable"
    }

    override fun toString(): String = "ReturnPages($origin)"
}
