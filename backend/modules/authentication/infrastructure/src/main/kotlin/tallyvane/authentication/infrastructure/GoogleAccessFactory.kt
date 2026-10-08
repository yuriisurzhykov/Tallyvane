package tallyvane.authentication.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import tallyvane.authentication.application.port.Google
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Surface

/**
 * Hands out the way this module speaks to Google, as the port the application layer uses.
 */
public class GoogleAccessFactory {
    /**
     * Google as the application registered with it: signing in sends the browser to Google, which
     * sends it back to [appRedirectUri] when the person is on the console and to [adminRedirectUri] when
     * they are on the administrators' site (ADR-097). Both are registered with Google.
     *
     * The HTTP client lives as long as the process; it holds no connection until a code is traded.
     */
    public fun google(
        clientId: String,
        clientSecret: Secret,
        appRedirectUri: String,
        adminRedirectUri: String,
    ): Google = GoogleOverHttp(
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MILLIS
                requestTimeoutMillis = TIMEOUT_MILLIS
            }
        },
        GoogleClient(
            clientId,
            clientSecret,
            mapOf(Surface.App to appRedirectUri, Surface.Admin to adminRedirectUri),
        ),
        GoogleEndpoints.google(),
    )

    override fun toString(): String = "GoogleAccessFactory"

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
