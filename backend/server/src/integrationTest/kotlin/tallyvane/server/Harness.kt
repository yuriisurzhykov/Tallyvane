package tallyvane.server

import org.slf4j.event.Level
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DEFAULT_SIZE
import tallyvane.platform.persistence.DatabaseAccess
import tallyvane.server.config.Configuration
import tallyvane.server.config.EnvironmentConfiguration
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * A token that satisfies the only rule there is about one: at least as long as the floor. Derived
 * rather than written out, so there is no second place holding a literal that has to agree with
 * [EnvironmentConfiguration.TOKEN_FLOOR].
 */
internal val TOKEN: String = "t".repeat(EnvironmentConfiguration.TOKEN_FLOOR)

private const val PAUSE_MILLIS = 200L

private const val ATTEMPTS = 100

/**
 * A port nothing is listening on, released immediately. There is a race between releasing it and
 * binding it, and it is accepted: the alternative is a fixed port, which turns one busy port on one
 * machine into a permanently red suite.
 */
internal fun free(): Int = ServerSocket(0).use { it.localPort }

internal fun settings(access: DatabaseAccess, port: Int = free(), pool: Int = DEFAULT_SIZE): Configuration =
    Configuration(
        database = access,
        pool = pool,
        port = port,
        level = Level.INFO,
        healthToken = Secret(TOKEN),
        tokenPepper = Secret(TOKEN),
        tokenPepperVersion = EnvironmentConfiguration.DEFAULT_PEPPER_VERSION,
        cookieSecure = false,
        accessTokenTtl = EnvironmentConfiguration.DEFAULT_ACCESS_TOKEN_TTL_MINUTES.minutes,
        refreshTokenIdleTtl = EnvironmentConfiguration.DEFAULT_REFRESH_TOKEN_IDLE_TTL_MINUTES.minutes,
        refreshTokenAbsoluteCap = EnvironmentConfiguration.DEFAULT_REFRESH_TOKEN_ABSOLUTE_CAP_MINUTES.minutes,
        pendingAuthenticationTtl = EnvironmentConfiguration.DEFAULT_PENDING_AUTHENTICATION_TTL_MINUTES.minutes,
        signInRateLimitThreshold = EnvironmentConfiguration.DEFAULT_SIGN_IN_RATE_LIMIT_THRESHOLD,
        signInRateLimitWindow = EnvironmentConfiguration.DEFAULT_SIGN_IN_RATE_LIMIT_WINDOW_MINUTES.minutes,
        totpIssuer = EnvironmentConfiguration.DEFAULT_TOTP_ISSUER,
        google = null,
    )

/**
 * One request over a real socket, with the service token when asked for.
 *
 * The JDK's own client rather than Ktor's: the point of an integration case here is that a stranger
 * with a socket gets the documented answer, and a client from the same framework as the server
 * shares too much with it to be that stranger.
 */
internal fun get(port: Int, path: String, token: String? = null): HttpResponse<String> = HttpClient
    .newBuilder()
    .connectTimeout(10.seconds.toJavaDuration())
    .build()
    .use { client ->
        val request = HttpRequest
            .newBuilder(URI("http://localhost:$port$path"))
            .apply { token?.let { header("x-service-token", it) } }
            .GET()
            .build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
    }

/**
 * Polls [observed] until it reports [wanted], so a case can say "readiness comes back" without
 * pinning how long a health check takes to notice.
 *
 * @return the last value seen, so a failure names what was actually being answered.
 */
internal fun <T> awaited(wanted: T, observed: () -> T): T {
    var last = observed()
    repeat(ATTEMPTS) {
        last = observed()
        if (last == wanted) {
            return last
        }
        Thread.sleep(PAUSE_MILLIS)
    }
    return last
}
