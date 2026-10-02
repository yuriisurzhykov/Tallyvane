package tallyvane.platform.http

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * The origin tests give `Api` as the application's own, so a request that wants to be let through
 * [fromApp] says it comes from there (ADR-080).
 */
const val APP_ORIGIN = "https://app.example.test"

/**
 * Makes the request look like one sent by the application's own page, as a browser makes it.
 */
fun HttpRequestBuilder.fromApp() {
    header(HttpHeaders.Origin, APP_ORIGIN)
}
