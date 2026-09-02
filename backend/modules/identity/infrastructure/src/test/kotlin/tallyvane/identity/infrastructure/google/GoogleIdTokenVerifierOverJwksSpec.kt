package tallyvane.identity.infrastructure.google

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.user.Email
import java.net.InetSocketAddress
import java.net.URI
import java.security.KeyPairGenerator
import java.util.Date
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Runs a real signature check against a JWKS this spec serves itself — an in-process HTTP server
 * on loopback, not a mock of Nimbus's own API, so a change to how [GoogleIdTokenVerifierOverJwks]
 * calls the library is still exercised for real.
 */
class GoogleIdTokenVerifierOverJwksSpec :
    StringSpec({
        val clientId = "test-client-id.apps.googleusercontent.com"
        val issuer = "https://accounts.google.com"
        val keyId = "test-key-1"
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_KEY_SIZE) }.generateKeyPair()
        val rsaKey = RSAKey.Builder(keyPair.public as java.security.interfaces.RSAPublicKey)
            .privateKey(keyPair.private)
            .keyID(keyId)
            .keyUse(KeyUse.SIGNATURE)
            .build()

        fun tokenFor(
            subject: String = "108234567890123456789",
            email: String = "person@example.com",
            emailVerified: Boolean = true,
            audience: String = clientId,
            issuedBy: String = issuer,
            expiresIn: kotlin.time.Duration = 5.minutes,
        ): String {
            val claims = JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuedBy)
                .audience(audience)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .expirationTime(Date.from(java.time.Instant.now().plus(expiresIn.toJavaDuration())))
                .build()
            val signed = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), claims)
            signed.sign(RSASSASigner(rsaKey))
            return signed.serialize()
        }

        suspend fun withJwksServer(block: suspend (verifier: GoogleIdTokenVerifierOverJwks) -> Unit) {
            val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
            val jwkSetJson = """{"keys":[${rsaKey.toPublicJWK().toJSONString()}]}"""
            server.createContext("/certs") { exchange ->
                val bytes = jwkSetJson.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            try {
                val jwksUri = URI("http://localhost:${server.address.port}/certs")
                block(GoogleIdTokenVerifierOverJwks(clientId, jwksUri, issuer))
            } finally {
                server.stop(0)
            }
        }

        "a genuinely signed, current token verifies and reads off subject and email" {
            withJwksServer { verifier ->
                val identity = verifier.verify(tokenFor())

                identity shouldBe GoogleIdentity(GoogleSubject("108234567890123456789"), Email("person@example.com"))
            }
        }

        "a token for a different audience is rejected" {
            withJwksServer { verifier ->
                verifier.verify(tokenFor(audience = "someone-elses-client-id")).shouldBeNull()
            }
        }

        "a token from a different issuer is rejected" {
            withJwksServer { verifier ->
                verifier.verify(tokenFor(issuedBy = "https://not-google.example")).shouldBeNull()
            }
        }

        "an expired token is rejected" {
            withJwksServer { verifier ->
                verifier.verify(tokenFor(expiresIn = (-5).minutes)).shouldBeNull()
            }
        }

        "a token whose email is not verified is rejected" {
            withJwksServer { verifier ->
                verifier.verify(tokenFor(emailVerified = false)).shouldBeNull()
            }
        }

        "a token signed by a key that is not in the published JWKS is rejected" {
            val impostorKeyPair = KeyPairGenerator.getInstance("RSA")
                .apply { initialize(RSA_KEY_SIZE) }
                .generateKeyPair()
            val impostorKey = RSAKey.Builder(impostorKeyPair.public as java.security.interfaces.RSAPublicKey)
                .privateKey(impostorKeyPair.private)
                .keyID(keyId)
                .build()
            val claims = JWTClaimsSet.Builder().subject("someone").issuer(issuer).audience(clientId).build()
            val forged = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), claims)
            forged.sign(RSASSASigner(impostorKey))

            withJwksServer { verifier ->
                verifier.verify(forged.serialize()).shouldBeNull()
            }
        }

        "a malformed token string is rejected, not thrown" {
            withJwksServer { verifier ->
                verifier.verify("not-a-jwt-at-all").shouldBeNull()
            }
        }
    })

private const val RSA_KEY_SIZE = 2048
