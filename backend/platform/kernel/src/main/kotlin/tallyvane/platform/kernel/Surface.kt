package tallyvane.platform.kernel

/**
 * Which of the application's two doors a request came through: the console people use, or the site its
 * administrators use (ADR-097).
 *
 * The two doors share one backend and one set of sign-in routes. What differs is data about the request,
 * read once at the edge, and it decides what a sign-in is for, where Google sends the browser back to and
 * which kind of session is issued. Kept here, beside `Secret` and `Clock`, because both the platform and the
 * modules that issue and recognise sessions need the same word.
 */
public enum class Surface {
    /**
     * The console, served from `app.<domain>`.
     */
    App,

    /**
     * The administrators' site, served from `admin.<domain>`.
     */
    Admin,
}
