package tallyvane.server.config

import org.slf4j.event.Level
import tallyvane.platform.kernel.Environment
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DEFAULT_SIZE
import tallyvane.platform.persistence.DatabaseAccess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Reads [Configuration] out of the process environment, and refuses the whole of it at once.
 *
 * Two properties a caller can rely on. A refusal lists **every** variable that is missing or
 * unusable, so one failed start is enough to learn everything that has to be fixed. And a refusal
 * never quotes a value, only the name of the variable and what was expected of it — the message is
 * read by whoever is fixing a deploy, and it must be safe to paste.
 *
 * @param environment where the values come from.
 */
public class EnvironmentConfiguration(private val environment: Environment) {
    /**
     * @return the settings, all of them valid.
     * @throws IllegalStateException naming **every** variable that is missing or unusable.
     */
    public fun read(): Configuration {
        val faults = mutableListOf<String>()
        val settings =
            Configuration(
                database =
                DatabaseAccess(
                    url = text(URL, faults),
                    user = text(USER, faults),
                    password = Secret(text(PASSWORD, faults)),
                ),
                pool = number(POOL, DEFAULT_SIZE, MIN_POOL..MAX_POOL, faults),
                port = number(PORT, DEFAULT_PORT, MIN_PORT..MAX_PORT, faults),
                level = level(faults),
                healthToken = token(HEALTH_TOKEN, faults),
                tokenPepper = token(TOKEN_PEPPER, faults),
                tokenPepperVersion = number(
                    TOKEN_PEPPER_VERSION,
                    DEFAULT_PEPPER_VERSION,
                    MIN_PEPPER_VERSION..MAX_PEPPER_VERSION,
                    faults,
                ),
                cookieSecure = boolean(COOKIE_SECURE, default = false, faults),
                accessTokenTtl = minutes(ACCESS_TOKEN_TTL_MINUTES, DEFAULT_ACCESS_TOKEN_TTL_MINUTES, faults),
                refreshTokenIdleTtl = minutes(REFRESH_TOKEN_IDLE_TTL_MINUTES, DEFAULT_REFRESH_TOKEN_IDLE_TTL_MINUTES, faults),
                refreshTokenAbsoluteCap = minutes(
                    REFRESH_TOKEN_ABSOLUTE_CAP_MINUTES,
                    DEFAULT_REFRESH_TOKEN_ABSOLUTE_CAP_MINUTES,
                    faults,
                ),
                pendingAuthenticationTtl = minutes(
                    PENDING_AUTHENTICATION_TTL_MINUTES,
                    DEFAULT_PENDING_AUTHENTICATION_TTL_MINUTES,
                    faults,
                ),
                signInRateLimitThreshold = number(
                    SIGN_IN_RATE_LIMIT_THRESHOLD,
                    DEFAULT_SIGN_IN_RATE_LIMIT_THRESHOLD,
                    MIN_RATE_LIMIT_THRESHOLD..MAX_RATE_LIMIT_THRESHOLD,
                    faults,
                ),
                signInRateLimitWindow = minutes(
                    SIGN_IN_RATE_LIMIT_WINDOW_MINUTES,
                    DEFAULT_SIGN_IN_RATE_LIMIT_WINDOW_MINUTES,
                    faults,
                ),
                totpIssuer = environment.read(TOTP_ISSUER)?.takeIf { it.isNotBlank() } ?: DEFAULT_TOTP_ISSUER,
                google = google(faults),
            )
        check(faults.isEmpty()) {
            faults.joinToString(separator = "\n", prefix = "Refusing to start.\n") { "  - $it" }
        }
        return settings
    }

    /**
     * A mandatory value. Blank counts as absent, so whitespace fails at startup rather than at the
     * first request that needs it.
     */
    private fun text(name: String, faults: MutableList<String>): String =
        environment.read(name)?.takeIf { it.isNotBlank() }
            ?: "".also { faults += "$name is not set" }

    private fun number(name: String, fallback: Int, allowed: IntRange, faults: MutableList<String>): Int {
        val raw = environment.read(name) ?: return fallback
        val parsed = raw.toIntOrNull()
        return when {
            parsed == null -> fallback.also { faults += "$name is not a whole number" }
            parsed !in allowed -> fallback.also { faults += "$name is outside $allowed" }
            else -> parsed
        }
    }

    private fun level(faults: MutableList<String>): Level {
        val raw = environment.read(LEVEL) ?: DEFAULT_LEVEL
        return Level.entries.firstOrNull { it.name == raw.uppercase() }
            ?: Level.INFO.also {
                faults += "$LEVEL is not one of ${Level.entries.joinToString { level -> level.name }}"
            }
    }

    /**
     * Mandatory, and at least [TOKEN_FLOOR] characters — shared by every secret this class reads
     * that guards something by its own length rather than a signature: an absent one blocks
     * whatever it guards, a short one opens that thing to anybody willing to guess, which is
     * worse.
     */
    private fun token(name: String, faults: MutableList<String>): Secret {
        val raw = environment.read(name)
        return when {
            raw == null -> Secret("").also { faults += "$name is not set" }
            raw.length < TOKEN_FLOOR -> Secret("").also { faults += "$name is shorter than $TOKEN_FLOOR characters" }
            else -> Secret(raw)
        }
    }

    /**
     * Optional, unset counts as [default] rather than a fault — `"true"`/`"false"` only, so a typo
     * such as `"yes"` is caught rather than silently read as false.
     */
    private fun boolean(name: String, default: Boolean, faults: MutableList<String>): Boolean {
        val raw = environment.read(name) ?: return default
        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default.also { faults += "$name must be 'true' or 'false'" }
        }
    }

    /**
     * A number of minutes, read the same way [number] reads any other optional integer, then
     * turned into a [Duration] — every session/token lifetime this process runs on is configured
     * in minutes, including the ones long enough to be read more naturally in days (a 90-day
     * absolute cap is `90 * 24 * 60`), so one unit and one parser cover all of them.
     */
    private fun minutes(name: String, fallbackMinutes: Int, faults: MutableList<String>): Duration =
        number(name, fallbackMinutes, MIN_MINUTES..MAX_MINUTES, faults).minutes

    /**
     * All three of [GoogleOAuthConfig]'s fields, or none — unlike [token], a variable missing here
     * is not itself a fault: neither Google sign-in method is required for the rest of `identity`
     * to run. A fault is raised only for a *partial* configuration (one or two of the three set),
     * since that is not "Google sign-in is off", it is a deploy that forgot a variable.
     */
    private fun google(faults: MutableList<String>): GoogleOAuthConfig? {
        val clientId = environment.read(GOOGLE_CLIENT_ID)?.takeIf { it.isNotBlank() }
        val clientSecret = environment.read(GOOGLE_CLIENT_SECRET)?.takeIf { it.isNotBlank() }
        val redirectUri = environment.read(GOOGLE_REDIRECT_URI)?.takeIf { it.isNotBlank() }
        val present = listOfNotNull(clientId, clientSecret, redirectUri).size
        return when (present) {
            0 -> null
            THREE -> GoogleOAuthConfig(clientId!!, Secret(clientSecret!!), redirectUri!!)
            else -> null.also {
                faults += "$GOOGLE_CLIENT_ID, $GOOGLE_CLIENT_SECRET and $GOOGLE_REDIRECT_URI must be set together or not at all"
            }
        }
    }

    public companion object {
        /**
         * The contract with the deploy. Public so that a test can pin the names, and gathered in
         * one place so that anything else needing them reads them from here instead of repeating
         * the strings.
         */
        public const val URL: String = "TALLYVANE_DB_URL"

        public const val USER: String = "TALLYVANE_DB_USER"

        public const val PASSWORD: String = "TALLYVANE_DB_PASSWORD"

        public const val HEALTH_TOKEN: String = "TALLYVANE_HEALTH_TOKEN"

        public const val PORT: String = "TALLYVANE_HTTP_PORT"

        public const val LEVEL: String = "TALLYVANE_LOG_LEVEL"

        public const val POOL: String = "TALLYVANE_DB_POOL_SIZE"

        /**
         * The server-side key `TokenHasher.Hmac` mixes into every access/refresh token hash — see
         * `identity/application/README.md` for why a pepper, not a per-user salt.
         */
        public const val TOKEN_PEPPER: String = "TALLYVANE_TOKEN_PEPPER"

        /**
         * Which pepper minted a hash already in storage — bumped only when the pepper itself
         * rotates, which invalidates every session hashed under the old one. Defaults to 1, the
         * version a fresh deployment starts on.
         */
        public const val TOKEN_PEPPER_VERSION: String = "TALLYVANE_TOKEN_PEPPER_VERSION"

        /**
         * Whether the session/refresh cookies carry the `Secure` attribute. `false` in
         * development, where the server answers over plain `http://localhost` and a `Secure`
         * cookie would simply never be stored — measured against Postman specifically, which
         * (unlike a browser) does not special-case `localhost`. `true` wherever the process sits
         * behind real TLS.
         */
        public const val COOKIE_SECURE: String = "TALLYVANE_COOKIE_SECURE"

        public const val ACCESS_TOKEN_TTL_MINUTES: String = "TALLYVANE_ACCESS_TOKEN_TTL_MINUTES"

        public const val REFRESH_TOKEN_IDLE_TTL_MINUTES: String = "TALLYVANE_REFRESH_TOKEN_IDLE_TTL_MINUTES"

        /**
         * RFC 9700 §4.14.2's absolute cap: a session stops being refreshable this long after it was
         * first issued, no matter how often it was used in between.
         */
        public const val REFRESH_TOKEN_ABSOLUTE_CAP_MINUTES: String = "TALLYVANE_REFRESH_TOKEN_ABSOLUTE_CAP_MINUTES"

        /**
         * How long a [tallyvane.identity.domain.secondfactor.PendingAuthentication] stays
         * redeemable after a primary credential checks out.
         */
        public const val PENDING_AUTHENTICATION_TTL_MINUTES: String = "TALLYVANE_PENDING_AUTHENTICATION_TTL_MINUTES"

        public const val SIGN_IN_RATE_LIMIT_THRESHOLD: String = "TALLYVANE_SIGN_IN_RATE_LIMIT_THRESHOLD"

        public const val SIGN_IN_RATE_LIMIT_WINDOW_MINUTES: String = "TALLYVANE_SIGN_IN_RATE_LIMIT_WINDOW_MINUTES"

        /**
         * The issuer name every enrolled authenticator app shows next to a TOTP code.
         */
        public const val TOTP_ISSUER: String = "TALLYVANE_TOTP_ISSUER"

        public const val GOOGLE_CLIENT_ID: String = "TALLYVANE_GOOGLE_CLIENT_ID"

        public const val GOOGLE_CLIENT_SECRET: String = "TALLYVANE_GOOGLE_CLIENT_SECRET"

        public const val GOOGLE_REDIRECT_URI: String = "TALLYVANE_GOOGLE_REDIRECT_URI"

        /**
         * Long enough that nobody types one by accident.
         */
        public const val TOKEN_FLOOR: Int = 40

        public const val DEFAULT_PORT: Int = 8080

        public const val DEFAULT_LEVEL: String = "INFO"

        public const val DEFAULT_PEPPER_VERSION: Int = 1

        /**
         * The design's own defaults, agreed before the first slice that needed them:
         * `backend/.plans/identity-implementation.md`'s opening section.
         */
        public const val DEFAULT_ACCESS_TOKEN_TTL_MINUTES: Int = 15

        private const val MINUTES_PER_DAY: Int = 24 * 60

        public const val DEFAULT_REFRESH_TOKEN_IDLE_TTL_MINUTES: Int = 30 * MINUTES_PER_DAY

        public const val DEFAULT_REFRESH_TOKEN_ABSOLUTE_CAP_MINUTES: Int = 90 * MINUTES_PER_DAY

        public const val DEFAULT_PENDING_AUTHENTICATION_TTL_MINUTES: Int = 5

        public const val DEFAULT_SIGN_IN_RATE_LIMIT_THRESHOLD: Int = 5

        public const val DEFAULT_SIGN_IN_RATE_LIMIT_WINDOW_MINUTES: Int = 15

        public const val DEFAULT_TOTP_ISSUER: String = "Tallyvane"

        public const val MIN_PORT: Int = 1

        public const val MAX_PORT: Int = 65_535

        public const val MIN_POOL: Int = 1

        public const val MIN_PEPPER_VERSION: Int = 1

        /**
         * A sanity bound that catches a typo, not a real ceiling — pepper rotations are rare and
         * manual.
         */
        public const val MAX_PEPPER_VERSION: Int = 1_000

        /**
         * A sanity bound that catches a typo, not a tuning limit: the real ceiling is the server's
         * `max_connections`, which this class cannot see.
         */
        public const val MAX_POOL: Int = 32

        /**
         * A floor of zero rather than one: a threshold of zero legitimately means "never allow a
         * failed attempt", refusing every sign-in after the first failure — a stricter policy a
         * deploy is allowed to choose, not a mistake to reject.
         */
        public const val MIN_RATE_LIMIT_THRESHOLD: Int = 0

        public const val MAX_RATE_LIMIT_THRESHOLD: Int = 1_000

        /**
         * A minute floor of zero rather than one, for the same reason as
         * [MIN_RATE_LIMIT_THRESHOLD]: an operator choosing "no grace period at all" is a real,
         * legal choice, not a typo to refuse.
         */
        public const val MIN_MINUTES: Int = 0

        /**
         * A sanity bound, not a tuning limit — about ten years in minutes, comfortably above the
         * longest lifetime this process actually issues (the 90-day absolute cap).
         */
        public const val MAX_MINUTES: Int = 5_256_000

        private const val THREE = 3
    }
}
