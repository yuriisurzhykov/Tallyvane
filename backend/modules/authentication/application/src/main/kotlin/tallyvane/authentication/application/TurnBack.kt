package tallyvane.authentication.application

/**
 * Why a return from Google led back to the sign-in page instead of onward.
 *
 * Each is something the person can act on differently, which is why there are six and not one; how
 * each is worded is the page's business.
 */
public enum class TurnBack {
    /**
     * There is no sign-in to continue: no cookie, an attempt already used or forgotten, a `state`
     * that does not match, or a step that is not Google's. Whatever it was, starting again fixes it.
     */
    Restart,

    /**
     * The attempt outlived its lifetime while the person was at Google.
     */
    Expired,

    /**
     * The person said no at Google, or closed the consent screen.
     */
    Cancelled,

    /**
     * Google did not accept the code, or its answer did not hold up.
     */
    Refused,

    /**
     * Google has not verified the person's address.
     */
    EmailUnverified,

    /**
     * Google could not be reached. Trying again later may work.
     */
    Unavailable,
}
