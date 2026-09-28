package tallyvane.server.config

import tallyvane.platform.kernel.Secret

/**
 * Google's own credentials for the OAuth Authorization Code + PKCE and Identity Services sign-in
 * methods — `null` on [Configuration] rather than three fields that are each independently
 * mandatory-or-blank: neither Google sign-in method is required for the rest of `identity` to run,
 * per the author's own decision recorded in `backend/.plans/identity-implementation.md`'s opening
 * section, so its absence must not refuse the whole process the way [Configuration.tokenPepper]'s
 * does.
 */
public class GoogleOAuthConfig(
    public val clientId: String,
    public val clientSecret: Secret,
    public val redirectUri: String,
)
