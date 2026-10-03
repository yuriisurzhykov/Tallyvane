package tallyvane.authentication.web

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import kotlin.time.Duration

/**
 * How a wait is told to a browser: whole seconds, never fewer than one, rounded up so that a client
 * which waits as long as it is told is not turned away again.
 */
internal class RetryAfter {
    /**
     * [wait] in whole seconds.
     */
    fun seconds(wait: Duration): Int = maxOf(1L, (wait.inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

    /**
     * Puts the wait in the `Retry-After` header of the answer to [call].
     */
    fun tell(call: ApplicationCall, wait: Duration) {
        call.response.header(HttpHeaders.RetryAfter, seconds(wait))
    }

    override fun toString(): String = "RetryAfter"

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
