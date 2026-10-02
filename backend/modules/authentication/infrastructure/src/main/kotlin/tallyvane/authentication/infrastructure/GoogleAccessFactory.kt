package tallyvane.authentication.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import tallyvane.authentication.application.port.Google
import tallyvane.platform.kernel.Secret

/**
 * Hands out the way this module speaks to Google, as the port the application layer uses.
 */
public class GoogleAccessFactory {
    /**
     * Google as the application registered with it: signing in sends the browser to Google, which
     * sends it back to [redirectUri].
     *
     * The HTTP client lives as long as the process; it holds no connection until a code is traded.
     */
    public fun google(clientId: String, clientSecret: Secret, redirectUri: String): Google = GoogleOverHttp(
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MILLIS
                requestTimeoutMillis = TIMEOUT_MILLIS
            }
        },
        GoogleClient(clientId, clientSecret, redirectUri),
        GoogleEndpoints.google(),
    )

    override fun toString(): String = "GoogleAccessFactory"

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
