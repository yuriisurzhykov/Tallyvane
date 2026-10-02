package tallyvane.authentication.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tallyvane.authentication.application.GoogleAnswer
import tallyvane.authentication.application.GoogleHandshake
import tallyvane.authentication.application.port.Google
import tallyvane.platform.kernel.Secret
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

/**
 * [Google] over HTTP: OpenID Connect, Authorization Code with PKCE (RFC 7636), run by the backend.
 *
 * The browser is sent to Google with the handshake's `state`, `nonce` and the S256 challenge of its
 * verifier. The code that comes back is traded at the token endpoint together with the client secret
 * and the verifier itself, and the ID token in the answer is read by [GoogleIdTokens].
 *
 * Every way this can fail is an answer. A `400` or `401` from the token endpoint is Google refusing
 * the code (`invalid_grant`); a network failure, a timeout or a `5xx` is Google being out of reach.
 */
internal class GoogleOverHttp(
    private val http: HttpClient,
    private val client: GoogleClient,
    private val endpoints: GoogleEndpoints,
) : Google {
    private val tokens = GoogleIdTokens(client.id(), endpoints)

    override fun addressFor(handshake: GoogleHandshake): String {
        val told = Told().also { handshake.writeTo(it) }
        return URLBuilder(endpoints.authorization).apply {
            parameters.append("client_id", client.id())
            parameters.append("redirect_uri", client.redirectUri())
            parameters.append("response_type", "code")
            parameters.append("scope", "openid email profile")
            parameters.append("state", told.state())
            parameters.append("nonce", told.nonce())
            parameters.append("code_challenge", challengeOf(told.verifier()))
            parameters.append("code_challenge_method", "S256")
            parameters.append("prompt", "select_account")
        }.buildString()
    }

    override suspend fun exchange(code: String, handshake: GoogleHandshake): GoogleAnswer {
        val told = Told().also { handshake.writeTo(it) }
        val response = try {
            http.submitForm(endpoints.token, formFor(code, told))
        } catch (@Suppress("SwallowedException") unreachable: IOException) {
            return GoogleAnswer.Unreachable()
        }
        return answerTo(response, told)
    }

    private suspend fun answerTo(response: HttpResponse, told: Told): GoogleAnswer = when {
        response.status.isSuccess() -> tokenIn(response.bodyAsText())?.let { tokens.read(it, Secret(told.nonce())) }
            ?: GoogleAnswer.Refused()
        response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Unauthorized ->
            GoogleAnswer.Refused()
        else -> GoogleAnswer.Unreachable()
    }

    private fun formFor(code: String, told: Told): Parameters = Parameters.build {
        append("code", code)
        append("client_id", client.id())
        append("client_secret", client.secret())
        append("redirect_uri", client.redirectUri())
        append("grant_type", "authorization_code")
        append("code_verifier", told.verifier())
    }

    private fun tokenIn(body: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject["id_token"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun challengeOf(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    override fun toString(): String = "GoogleOverHttp($endpoints)"

    /**
     * The three secrets of a handshake, as the strings they go on the wire as.
     */
    private class Told : GoogleHandshake.Record {
        private val heard = mutableListOf<Triple<String, String, String>>()

        override fun handshake(state: Secret, nonce: Secret, verifier: Secret) {
            heard += Triple(state.revealed(), nonce.revealed(), verifier.revealed())
        }

        fun state(): String = heard.single().first

        fun nonce(): String = heard.single().second

        fun verifier(): String = heard.single().third
    }
}
