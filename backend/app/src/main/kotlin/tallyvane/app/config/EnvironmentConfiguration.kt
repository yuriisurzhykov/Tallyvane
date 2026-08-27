package tallyvane.app.config

import tallyvane.platform.kernel.Environment

/**
 * Reads [Configuration] out of the process environment, and refuses the whole of it at once.
 *
 * Skeleton: the body is `TODO()` until `EnvironmentConfigurationSpec` is seen failing.
 *
 * @param environment where the values come from. A port rather than `System.getenv`, because the
 * JVM exposes the process environment read-only and a test could otherwise only describe whatever
 * the machine happens to have.
 */
public class EnvironmentConfiguration(private val environment: Environment) {
    /**
     * @return the settings, all of them valid.
     * @throws IllegalStateException naming **every** variable that is missing or unusable, not the
     * first one found. A deploy that has to be fixed four times because each attempt reveals one
     * more missing variable is four failed deploys.
     */
    public fun read(): Configuration = TODO()

    public companion object {
        /**
         * The names live here rather than beside each field so that one test can pin them, and so
         * that `:migrate` — which needs the same three — can eventually read them from one place
         * instead of holding its own copies. They are a contract with the deploy, and a contract
         * written twice is a contract nothing notices diverging.
         */
        public const val URL: String = "TALLYVANE_DB_URL"

        public const val USER: String = "TALLYVANE_DB_USER"

        public const val PASSWORD: String = "TALLYVANE_DB_PASSWORD"

        public const val HEALTH_TOKEN: String = "TALLYVANE_HEALTH_TOKEN"

        public const val PORT: String = "TALLYVANE_HTTP_PORT"

        public const val LEVEL: String = "TALLYVANE_LOG_LEVEL"

        public const val POOL: String = "TALLYVANE_DB_POOL_SIZE"

        /**
         * A token shorter than this is worse than none at all: it looks configured. Forty because
         * that is the floor the author set, and it is the same order as the reason length
         * `@ArchitectureException` demands — long enough that nobody types one by accident.
         */
        public const val TOKEN_FLOOR: Int = 40

        public const val DEFAULT_PORT: Int = 8080

        public const val DEFAULT_POOL: Int = 8

        public const val DEFAULT_LEVEL: String = "INFO"
    }
}
