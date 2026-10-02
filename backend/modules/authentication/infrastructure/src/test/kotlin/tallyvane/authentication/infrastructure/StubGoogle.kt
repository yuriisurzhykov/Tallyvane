package tallyvane.authentication.infrastructure

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import tallyvane.authentication.application.GoogleHandshake
import tallyvane.platform.kernel.Secret
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

/**
 * A provider of its own, standing where Google stands: it publishes keys, and trades a code for an ID
 * token only when the verifier matches the challenge the code was issued against, as Google does.
 *
 * Real HTTP on loopback and a real signature, so [GoogleOverHttp] is exercised the way it runs, not
 * against a mock of the libraries it calls.
 *
 * What goes wrong is arranged per token through the fields: a stub that is never told anything signs
 * a perfectly good token.
 */
class StubGoogle : AutoCloseable {
    private val key = newKey()
    private val impostor = newKey()
    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    private val issued = mutableMapOf<String, Issued>()
    private var sequence = 0

    var audience = CLIENT_ID
    var issuer: String? = null
    var nonceKept = true
    var lifetimeMillis = FIVE_MINUTES
    var signedByImpostor = false
    var tokenStatus: Int? = null

    init {
        server.createContext("/keys") { exchange ->
            answer(exchange, 200, """{"keys":[${key.toPublicJWK().toJSONString()}]}""")
        }
        server.createContext("/token") { exchange -> trade(exchange) }
        server.start()
    }

    private val base get() = "http://localhost:${server.address.port}"

    internal fun endpoints(): GoogleEndpoints = GoogleEndpoints("$base/auth", "$base/token", "$base/keys", ISSUER)

    /**
     * A code for the person [subject], called [name], that only [handshake] can trade.
     */
    fun codeFor(handshake: GoogleHandshake, subject: String, name: String, email: String, verified: Boolean): String {
        val told = mutableListOf<Triple<String, String, String>>()
        handshake.writeTo { state, nonce, verifier ->
            told +=
                Triple(state.revealed(), nonce.revealed(), verifier.revealed())
        }
        val (_, nonce, verifier) = told.single()
        sequence += 1
        val code = "stub-code-$sequence"
        issued[code] = Issued(challengeOf(verifier), nonce, subject, name, email, verified)
        return code
    }

    fun address(): String = base

    override fun close() {
        server.stop(0)
    }

    private fun trade(exchange: HttpExchange) {
        val form = formOf(exchange)
        val granted = issued.remove(form["code"])
        when {
            tokenStatus != null -> answer(exchange, tokenStatus!!, "{}")
            granted == null -> answer(exchange, 400, """{"error":"invalid_grant"}""")
            challengeOf(form["code_verifier"].orEmpty()) != granted.challenge ->
                answer(exchange, 400, """{"error":"invalid_grant"}""")
            form["client_id"] != CLIENT_ID ||
                form["client_secret"] != CLIENT_SECRET.revealed() ||
                form["redirect_uri"] != REDIRECT_URI -> answer(exchange, 401, """{"error":"invalid_client"}""")
            else -> answer(exchange, 200, """{"id_token":"${tokenFor(granted)}","access_token":"unused"}""")
        }
    }

    private fun tokenFor(granted: Issued): String {
        val claims = JWTClaimsSet.Builder()
            .subject(granted.subject)
            .issuer(issuer ?: ISSUER)
            .audience(audience)
            .claim("email", granted.email)
            .claim("email_verified", granted.verified)
            .claim("name", granted.name)
            .claim("nonce", if (nonceKept) granted.nonce else "another-trips-nonce")
            .expirationTime(Date(System.currentTimeMillis() + lifetimeMillis))
            .build()
        val signer = if (signedByImpostor) impostor else key
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims)
            .also { it.sign(RSASSASigner(signer)) }
            .serialize()
    }

    private fun formOf(exchange: HttpExchange): Map<String, String> = exchange.requestBody.readBytes().decodeToString()
        .split("&").filter { it.contains("=") }
        .associate { pair ->
            pair.substringBefore("=").let { URLDecoder.decode(it, Charsets.UTF_8) } to
                pair.substringAfter("=").let { URLDecoder.decode(it, Charsets.UTF_8) }
        }

    private fun answer(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun challengeOf(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun newKey(): RSAKey {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_SIZE) }.generateKeyPair()
        return RSAKey.Builder(
            pair.public as RSAPublicKey,
        ).privateKey(pair.private).keyID(KEY_ID).keyUse(KeyUse.SIGNATURE).build()
    }

    override fun toString(): String = "StubGoogle($base)"

    private class Issued(
        val challenge: String,
        val nonce: String,
        val subject: String,
        val name: String,
        val email: String,
        val verified: Boolean,
    ) {
        override fun toString(): String = "Issued"
    }

    companion object {
        const val CLIENT_ID = "stub-client.apps.googleusercontent.com"
        val CLIENT_SECRET = Secret("stub-client-secret")
        const val REDIRECT_URI = "https://app.example.test/callback"
        private const val ISSUER = "https://accounts.google.com"
        private const val KEY_ID = "stub-key-1"
        private const val RSA_KEY_SIZE = 2048
        private const val FIVE_MINUTES = 300_000L
    }
}
