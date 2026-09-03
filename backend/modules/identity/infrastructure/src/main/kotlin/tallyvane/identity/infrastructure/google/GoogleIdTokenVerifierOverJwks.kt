package tallyvane.identity.infrastructure.google

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
import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.application.port.GoogleIdTokenVerifier
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.user.Email
import java.net.URI
import java.text.ParseException

/**
 * The real [GoogleIdTokenVerifier] — checks a token's signature against Google's own published
 * JWKS, and that `iss`/`aud` match Google and this app's own [clientId]. Shared by both Google
 * sign-in methods: Authorization Code exchange verifies the token it gets back from Google's
 * token endpoint; Google Identity Services verifies the token the browser's own Credential
 * Manager hands the client directly.
 *
 * [processor]'s key source ([JWKSourceBuilder]) caches the fetched keys and re-fetches only when a
 * `kid` it has never seen appears, so a merely-stale cache never produces a false rejection during
 * Google's own periodic key rotation.
 *
 * `ParseException` (malformed token text) and [BadJOSEException] (bad signature, expired, claims
 * mismatch) both answer `null` — the same "don't distinguish why a credential was refused" choice
 * [tallyvane.identity.application.password.SignInWithPasswordUseCase] already makes for a wrong
 * password. A genuine [com.nimbusds.jose.JOSEException] — Google's JWKS endpoint unreachable, an
 * unsupported algorithm — is not this class's to recover from and reaches the caller uncaught.
 *
 * [com.nimbusds.jwt.proc.ConfigurableJWTProcessor.process] is a plain blocking call — on a cache
 * miss it fetches the JWKS over the network synchronously — so it runs on [Dispatchers.IO], never
 * on the caller's own dispatcher.
 *
 * @param jwksEndpoint Where the signing keys are published — Google's own real endpoint by
 * default; a test's own local server otherwise, so this class is verified against a real Nimbus
 * call, not a mock of one.
 * @param issuer The `iss` claim a genuine token must carry.
 */
internal class GoogleIdTokenVerifierOverJwks(
    private val clientId: String,
    jwksEndpoint: URI = URI(DEFAULT_JWKS_ENDPOINT),
    issuer: String = DEFAULT_ISSUER,
) : GoogleIdTokenVerifier {
    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, keySource(jwksEndpoint))
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            JWTClaimsSet.Builder().issuer(issuer).audience(clientId).build(),
            REQUIRED_CLAIMS,
        )
    }

    @Suppress("SwallowedException") // by design: every rejection reason collapses to null, see above
    override suspend fun verify(idToken: String): GoogleIdentity? = withContext(Dispatchers.IO) {
        try {
            val claims = processor.process(idToken, null)
            if (claims.getBooleanClaim(EMAIL_VERIFIED_CLAIM) != true) {
                null
            } else {
                GoogleIdentity(GoogleSubject(claims.subject), Email(claims.getStringClaim(EMAIL_CLAIM)))
            }
        } catch (failure: ParseException) {
            null
        } catch (failure: BadJOSEException) {
            null
        }
    }

    private fun keySource(jwksEndpoint: URI): JWKSource<SecurityContext> =
        JWKSourceBuilder.create<SecurityContext>(jwksEndpoint.toURL()).build()

    private companion object {
        const val DEFAULT_JWKS_ENDPOINT = "https://www.googleapis.com/oauth2/v3/certs"
        const val DEFAULT_ISSUER = "https://accounts.google.com"
        const val EMAIL_CLAIM = "email"
        const val EMAIL_VERIFIED_CLAIM = "email_verified"
        val REQUIRED_CLAIMS = setOf("sub", "email", "email_verified", "exp")
    }
}
