package tallyvane.identity.infrastructure.googleoauth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.application.port.GoogleIdTokenVerifier
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.platform.kernel.Secret

/**
 * The real [GoogleOAuthGateway] — posts the authorization code to Google's own token endpoint
 * over [httpClient], then hands the ID token it returns to [idTokenVerifier].
 *
 * ```
 * gateway.exchangeCode(code, verifier, redirectUri) // POSTs to Google, then verifies its answer
 * ```
 *
 * `400 Bad Request` from Google's token endpoint (`invalid_grant`) answers `null`, per this
 * port's own contract; any other failure — a network fault, an unexpected response shape — is not
 * caught here and reaches the caller uncaught.
 */
internal class GoogleOAuthGatewayOverHttp(
    private val httpClient: HttpClient,
    private val clientId: String,
    private val clientSecret: Secret,
    private val idTokenVerifier: GoogleIdTokenVerifier,
) : GoogleOAuthGateway {
    override suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): GoogleIdentity? {
        val response = httpClient.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("code", code)
                append("client_id", clientId)
                append("client_secret", clientSecret.revealed())
                append("redirect_uri", redirectUri)
                append("grant_type", "authorization_code")
                append("code_verifier", codeVerifier)
            },
        )
        if (response.status == HttpStatusCode.BadRequest) {
            return null
        }
        val token = Json.decodeFromString<TokenResponse>(response.bodyAsText())
        return idTokenVerifier.verify(token.idToken)
    }

    @Serializable
    private data class TokenResponse(@SerialName("id_token") val idToken: String)

    private companion object {
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }
}
