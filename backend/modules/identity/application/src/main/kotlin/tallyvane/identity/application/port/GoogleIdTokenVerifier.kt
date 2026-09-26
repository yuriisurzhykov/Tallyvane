package tallyvane.identity.application.port

import tallyvane.identity.application.google.GoogleIdentity

/**
 * Verifies a Google-issued ID token's signature and claims, and reads off the identity it
 * vouches for.
 *
 * ```
 * verifier.verify(idToken)                  // -> GoogleIdentity(subject, email), if genuine and current
 * verifier.verify(tamperedOrExpiredToken)    // -> null
 * ```
 *
 * `null` covers every rejection reason alike — bad signature, wrong audience, wrong issuer,
 * expired — the same "don't distinguish why a credential was refused" choice
 * [tallyvane.identity.application.password.SignInWithPasswordUseCase] already makes for a wrong
 * password.
 */
public interface GoogleIdTokenVerifier {
    public suspend fun verify(idToken: String): GoogleIdentity?
}
