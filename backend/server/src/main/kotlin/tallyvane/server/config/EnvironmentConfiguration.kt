package tallyvane.server.config

import org.slf4j.event.Level
import tallyvane.platform.kernel.Environment
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DEFAULT_SIZE
import tallyvane.platform.persistence.DatabaseAccess

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
                healthToken = token(faults),
                signIn = signIn(faults),
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
     * Mandatory, and at least [TOKEN_FLOOR] characters. An absent token closes the detailed health
     * report; a short one opens it to anybody willing to guess, which is worse.
     */
    private fun token(faults: MutableList<String>): Secret {
        val raw = environment.read(HEALTH_TOKEN)
        return when {
            raw == null -> Secret("").also { faults += "$HEALTH_TOKEN is not set" }
            raw.length < TOKEN_FLOOR ->
                Secret("").also { faults += "$HEALTH_TOKEN is shorter than $TOKEN_FLOOR characters" }
            else -> Secret(raw)
        }
    }

    private fun signIn(faults: MutableList<String>): SignInConfiguration = SignInConfiguration(
        googleClientId = text(GOOGLE_CLIENT_ID, faults),
        googleClientSecret = Secret(text(GOOGLE_CLIENT_SECRET, faults)),
        appOrigin = origin(APP_ORIGIN, faults),
        apiOrigin = origin(API_ORIGIN, faults),
        tokenPepper = pepper(faults),
        pepperVersion = number(TOKEN_PEPPER_VERSION, 1, 1..MAX_PEPPER_VERSION, faults),
        totpKeyset = Secret(text(TOTP_KEYSET, faults)),
    )

    /**
     * A mandatory origin: scheme and host, no path and no trailing slash, so `"$origin/path"` is
     * always a well-formed address.
     */
    private fun origin(name: String, faults: MutableList<String>): String {
        val raw = environment.read(name)?.takeIf { it.isNotBlank() }
        return when {
            raw == null -> "".also { faults += "$name is not set" }
            !ORIGIN.matches(raw) ->
                "".also { faults += "$name is not an origin like https://app.example.com, with no path or slash" }
            else -> raw
        }
    }

    /**
     * Mandatory, and at least [PEPPER_FLOOR] characters: a short pepper can be guessed, and then every
     * digest it made can be computed.
     */
    private fun pepper(faults: MutableList<String>): Secret {
        val raw = environment.read(TOKEN_PEPPER)
        return when {
            raw == null -> Secret("").also { faults += "$TOKEN_PEPPER is not set" }
            raw.length < PEPPER_FLOOR ->
                Secret("").also { faults += "$TOKEN_PEPPER is shorter than $PEPPER_FLOOR characters" }
            else -> Secret(raw)
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

        public const val GOOGLE_CLIENT_ID: String = "TALLYVANE_GOOGLE_CLIENT_ID"

        public const val GOOGLE_CLIENT_SECRET: String = "TALLYVANE_GOOGLE_CLIENT_SECRET"

        public const val APP_ORIGIN: String = "TALLYVANE_APP_ORIGIN"

        public const val API_ORIGIN: String = "TALLYVANE_API_ORIGIN"

        public const val TOKEN_PEPPER: String = "TALLYVANE_TOKEN_PEPPER"

        public const val TOKEN_PEPPER_VERSION: String = "TALLYVANE_TOKEN_PEPPER_VERSION"

        public const val TOTP_KEYSET: String = "TALLYVANE_TOTP_KEYSET"

        /**
         * As long as `Digests.Hmac` insists on.
         */
        public const val PEPPER_FLOOR: Int = 32

        public const val MAX_PEPPER_VERSION: Int = 1_000

        private val ORIGIN = Regex("^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$")

        /**
         * Long enough that nobody types one by accident.
         */
        public const val TOKEN_FLOOR: Int = 40

        public const val DEFAULT_PORT: Int = 8080

        public const val DEFAULT_LEVEL: String = "INFO"

        public const val MIN_PORT: Int = 1

        public const val MAX_PORT: Int = 65_535

        public const val MIN_POOL: Int = 1

        /**
         * A sanity bound that catches a typo, not a tuning limit: the real ceiling is the server's
         * `max_connections`, which this class cannot see.
         */
        public const val MAX_POOL: Int = 32
    }
}
