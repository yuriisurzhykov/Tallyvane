package tallyvane.authentication.infrastructure

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tallyvane.authentication.application.GoogleAnswer
import tallyvane.authentication.application.GoogleProfile
import tallyvane.platform.kernel.Secret
import java.net.URI
import java.text.ParseException

/**
 * Reads the ID token Google signs: the signature against Google's published keys, the issuer and the
 * audience, the expiry, and that the `nonce` inside is the one this trip sent.
 *
 * The key source caches what it fetched and fetches again only for a key id it has not seen, so
 * Google rotating its keys does not refuse anyone in the meantime. Verifying is a blocking call that
 * may fetch the keys over the network, so it runs on [Dispatchers.IO].
 *
 * A token that does not hold up is [GoogleAnswer.Refused], whatever was wrong with it: signature,
 * issuer, audience, expiry or nonce. The person can do the same about all of them, and which it was
 * is for the log, not for the page. Keys that cannot be fetched are [GoogleAnswer.Unreachable].
 */
internal class GoogleIdTokens(private val clientId: String, endpoints: GoogleEndpoints) {
    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, keysAt(endpoints.keys))
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            setOf(clientId),
            JWTClaimsSet.Builder().issuer(endpoints.issuer).build(),
            REQUIRED_CLAIMS,
            null,
        )
    }

    /**
     * What Google vouches for in [idToken], if it was minted for the trip whose nonce is [nonce].
     */
    suspend fun read(idToken: String, nonce: Secret): GoogleAnswer = withContext(Dispatchers.IO) {
        try {
            answerFor(processor.process(idToken, null), nonce)
        } catch (@Suppress("SwallowedException") unreadable: ParseException) {
            GoogleAnswer.Refused()
        } catch (@Suppress("SwallowedException") untrusted: BadJOSEException) {
            GoogleAnswer.Refused()
        } catch (@Suppress("SwallowedException") unreachable: JOSEException) {
            GoogleAnswer.Unreachable()
        }
    }

    private fun answerFor(claims: JWTClaimsSet, nonce: Secret): GoogleAnswer = when {
        Secret(claims.getStringClaim(NONCE_CLAIM).orEmpty()) != nonce -> GoogleAnswer.Refused()
        claims.getClaim(EMAIL_VERIFIED_CLAIM).toString() != "true" -> GoogleAnswer.EmailUnverified()
        else -> GoogleAnswer.Vouched(claims.subject, profileIn(claims))
    }

    private fun profileIn(claims: JWTClaimsSet): GoogleProfile {
        val email = claims.getStringClaim(EMAIL_CLAIM)
        return GoogleProfile(
            claims.getStringClaim(NAME_CLAIM)?.takeIf {
                it.isNotBlank()
            } ?: email.substringBefore('@'),
            email,
        )
    }

    private fun keysAt(address: String): JWKSource<SecurityContext> =
        JWKSourceBuilder.create<SecurityContext>(URI(address).toURL()).build()

    override fun toString(): String = "GoogleIdTokens(clientId=$clientId)"

    private companion object {
        const val NONCE_CLAIM = "nonce"
        const val EMAIL_CLAIM = "email"
        const val NAME_CLAIM = "name"
        const val EMAIL_VERIFIED_CLAIM = "email_verified"
        val REQUIRED_CLAIMS = setOf("sub", "email", "email_verified", "nonce", "exp")
    }
}
