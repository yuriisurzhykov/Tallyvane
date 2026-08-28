package tallyvane.app.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.event.Level
import tallyvane.platform.kernel.EnvironmentFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DEFAULT_SIZE

private const val PASSWORD_VALUE = "a-database-password-nobody-should-ever-see-in-a-log"

private const val TOKEN_VALUE = "a-service-token-of-at-least-forty-characters-length"

/**
 * A token under the floor, spelled so that it cannot turn up inside a refusal by coincidence — the
 * case below asserts a value is never quoted, and a sentinel that collides with the message's own
 * words would prove nothing. See `app/README.md`.
 */
private const val BRIEF_TOKEN = "qzx-under-the-floor-qzx"

/**
 * A complete environment, as a deploy would supply it. Cases that test a refusal remove one key
 * from this rather than building a partial map, so what the case is about is the difference.
 */
private fun complete(): MutableMap<String, String> = mutableMapOf(
    EnvironmentConfiguration.URL to "jdbc:postgresql://db:5432/tallyvane",
    EnvironmentConfiguration.USER to "tallyvane",
    EnvironmentConfiguration.PASSWORD to PASSWORD_VALUE,
    EnvironmentConfiguration.HEALTH_TOKEN to TOKEN_VALUE,
)

private fun read(values: Map<String, String>): Configuration = EnvironmentConfiguration(EnvironmentFake(values)).read()

private fun refusal(values: Map<String, String>): String =
    shouldThrow<IllegalStateException> { read(values) }.message.orEmpty()

class EnvironmentConfigurationSpec :
    StringSpec(
        {
            // A1
            "reads every value from the variable that carries it" {
                val configuration = read(complete())

                configuration.database.url shouldBe "jdbc:postgresql://db:5432/tallyvane"
                configuration.database.user shouldBe "tallyvane"
                configuration.database.password shouldBe Secret(PASSWORD_VALUE)
                configuration.healthToken shouldBe Secret(TOKEN_VALUE)
            }

            // A6
            "falls back to documented defaults for what a deploy may leave out" {
                val configuration = read(complete())

                configuration.port shouldBe EnvironmentConfiguration.DEFAULT_PORT
                configuration.pool shouldBe DEFAULT_SIZE
                configuration.level shouldBe Level.INFO
            }

            "takes the optional values from the environment when it does supply them" {
                val configuration = read(
                    complete().apply {
                        put(EnvironmentConfiguration.PORT, "9443")
                        put(EnvironmentConfiguration.POOL, "3")
                        put(EnvironmentConfiguration.LEVEL, "WARN")
                    },
                )

                configuration.port shouldBe 9443
                configuration.pool shouldBe 3
                configuration.level shouldBe Level.WARN
            }

            // A2
            "names the one variable that is missing" {
                val said = refusal(complete().apply { remove(EnvironmentConfiguration.USER) })

                said shouldContain EnvironmentConfiguration.USER
            }

            // A3 — the case of this whole spec. An implementation that refuses on the first
            // missing variable passes A2 and fails here, and the difference is four failed deploys
            // against one.
            "names every missing variable at once, not the first one it meets" {
                val said = refusal(
                    complete().apply {
                        remove(EnvironmentConfiguration.URL)
                        remove(EnvironmentConfiguration.PASSWORD)
                        remove(EnvironmentConfiguration.HEALTH_TOKEN)
                    },
                )

                said shouldContain EnvironmentConfiguration.URL
                said shouldContain EnvironmentConfiguration.PASSWORD
                said shouldContain EnvironmentConfiguration.HEALTH_TOKEN
            }

            // A4
            "refuses a number it cannot read, and says which variable held it" {
                val said = refusal(complete().apply { put(EnvironmentConfiguration.POOL, "eight") })

                said shouldContain EnvironmentConfiguration.POOL
            }

            "refuses a log level that does not exist, and says which variable held it" {
                val said = refusal(complete().apply { put(EnvironmentConfiguration.LEVEL, "CHATTY") })

                said shouldContain EnvironmentConfiguration.LEVEL
            }

            // A5
            "refuses a pool of zero, because a dispatcher of zero parallelism runs nothing" {
                val said = refusal(complete().apply { put(EnvironmentConfiguration.POOL, "0") })

                said shouldContain EnvironmentConfiguration.POOL
            }

            "refuses a port outside the range a port can have" {
                val said = refusal(complete().apply { put(EnvironmentConfiguration.PORT, "70000") })

                said shouldContain EnvironmentConfiguration.PORT
            }

            // The token is mandatory, and a short one is worse than none because it looks
            // configured. `ServiceToken` treats an empty secret as a closed door; this stops a
            // deploy from reaching that state by accident.
            "refuses a health token shorter than the floor" {
                val said = refusal(
                    complete().apply { put(EnvironmentConfiguration.HEALTH_TOKEN, BRIEF_TOKEN) },
                )

                said shouldContain EnvironmentConfiguration.HEALTH_TOKEN
            }

            // A7. The refusal message is read by whoever is fixing the deploy, and it is the most
            // likely place for a secret to be quoted "helpfully".
            "never quotes a value in a refusal, only the name of the variable" {
                val said = refusal(
                    complete().apply { put(EnvironmentConfiguration.HEALTH_TOKEN, BRIEF_TOKEN) },
                )

                said shouldNotContain BRIEF_TOKEN
                said shouldNotContain PASSWORD_VALUE
            }

            "does not print a secret when the whole configuration is printed" {
                val printed = read(complete()).database.toString()

                printed shouldNotContain PASSWORD_VALUE
                printed shouldContain "jdbc:postgresql://db:5432/tallyvane"
            }
        },
    )
