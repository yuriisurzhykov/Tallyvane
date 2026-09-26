package tallyvane.identity.application.port

import tallyvane.identity.application.google.GoogleIdentity

/**
 * Exchanges an OAuth authorization code for the identity Google vouches for — the Authorization
 * Code + PKCE flow's token-endpoint call, plus verifying the ID token it returns.
 *
 * ```
 * gateway.exchangeCode(code, codeVerifier, redirectUri)            // -> GoogleIdentity(...)
 * gateway.exchangeCode(alreadyUsedCode, codeVerifier, redirectUri) // -> null, Google refused it
 * ```
 *
 * `null` is Google's own token endpoint answering `invalid_grant` — an expired, already-used, or
 * otherwise rejected code, exactly as ordinary as a wrong password. A genuine fault reaching
 * Google at all — a network failure, an unexpected response shape — is not this port's to
 * recover from; it reaches the caller as an exception, uncaught, the same as any other adapter in
 * this codebase that only relays what a real dependency did.
 */
public interface GoogleOAuthGateway {
    public suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): GoogleIdentity?
}
