package tallyvane.app

import ch.qos.logback.classic.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import tallyvane.app.config.Configuration
import tallyvane.app.config.EnvironmentConfiguration
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DEFAULT_SIZE
import tallyvane.platform.persistence.DatabaseAccess
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Port 1 is not a database. Chosen over an unreachable host so the attempt is refused rather than
 * timing out, which keeps these cases in the region of milliseconds.
 */
private const val NOWHERE = "jdbc:postgresql://localhost:1/nothing"

/**
 * Derived from the only rule about a token, so no literal here has to agree with a constant
 * elsewhere.
 */
private val TOKEN = "t".repeat(EnvironmentConfiguration.TOKEN_FLOOR)

private fun free(): Int = ServerSocket(0).use { it.localPort }

private fun settings(port: Int, level: Level = Level.INFO): Configuration = Configuration(
    database = DatabaseAccess(url = NOWHERE, user = "nobody", password = Secret("nothing")),
    pool = DEFAULT_SIZE,
    port = port,
    level = level,
    healthToken = Secret(TOKEN),
)

private fun get(port: Int, path: String): HttpResponse<String> = HttpClient
    .newBuilder()
    .connectTimeout(10.seconds.toJavaDuration())
    .build()
    .use { client ->
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

private fun root(): Logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

/**
 * What the process does with no database anywhere. None of these need Docker, which is the point:
 * "comes up without a database" is exactly the case where there is nothing to start.
 */
class ApplicationSpec :
    StringSpec(
        {
            // C1. Fails on the behaviour measured in `playground/health` on 2026-08-26: the pool
            // threw from its constructor, so the process never reached the point of listening and
            // `/health` could not report a database being down at all.
            "comes up with no database at all, and liveness answers" {
                val port = free()

                Application(settings(port)).use { application ->
                    application.start()

                    val answer = get(port, "/api/v1/health/live")

                    answer.statusCode() shouldBe HttpStatusCode.OK.value
                    answer.body() shouldContain "up"
                }
            }

            // C2. The whole reason for coming up at all: readiness is what says "running, but do
            // not send me traffic". A wiring that reported ready without consulting its checks
            // would pass C1 and fail here.
            "answers not-ready while the database is unreachable" {
                val port = free()

                Application(settings(port)).use { application ->
                    application.start()

                    get(port, "/api/v1/health/ready").statusCode() shouldBe HttpStatusCode.ServiceUnavailable.value
                }
            }

            // D1. Fails while the level is hardcoded in the Logback fragment, which is where it
            // lives today.
            "puts the configured level on the root logger" {
                val port = free()
                val before = root().level

                try {
                    Application(settings(port, level = Level.WARN)).use { application ->
                        application.start()

                        root().level.toString() shouldBe "WARN"
                    }
                } finally {
                    root().level = before
                }
            }
        },
    )
