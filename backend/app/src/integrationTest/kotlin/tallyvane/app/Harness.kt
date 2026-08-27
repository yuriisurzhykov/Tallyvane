package tallyvane.app

import org.slf4j.event.Level
import tallyvane.app.config.Configuration
import tallyvane.platform.kernel.Secret
import tallyvane.platform.persistence.DatabaseAccess
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

internal const val TOKEN = "a-service-token-of-at-least-forty-characters-length"

private const val DEFAULT_POOL = 2

/**
 * Between polls. Short enough that a case does not sit on a state it already left, long enough that
 * a hundred attempts still span twenty seconds.
 */
private const val PAUSE_MILLIS = 200L

/**
 * A port nothing is listening on, released immediately. There is a race between releasing it and
 * binding it, and it is accepted: the alternative is a fixed port, which turns one busy port on one
 * machine into a permanently red suite.
 */
internal fun free(): Int = ServerSocket(0).use { it.localPort }

internal fun settings(access: DatabaseAccess, port: Int = free(), pool: Int = DEFAULT_POOL): Configuration =
    Configuration(
        database = access,
        pool = pool,
        port = port,
        level = Level.INFO,
        healthToken = Secret(TOKEN),
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
 * Polls [answered] until it reports the wanted status, so a case can say "readiness comes back"
 * without pinning how long a health check takes to notice.
 *
 * @return the last status seen, so a failure names what was actually being answered.
 */
internal fun awaited(wanted: Int, within: Int = 100, answered: () -> Int): Int {
    var last = 0
    repeat(within) {
        last = answered()
        if (last == wanted) {
            return last
        }
        Thread.sleep(PAUSE_MILLIS)
    }
    return last
}
