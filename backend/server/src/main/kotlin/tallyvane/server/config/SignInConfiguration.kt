package tallyvane.server.config

import tallyvane.platform.kernel.Secret

/**
 * What signing in needs from the deploy: this application as Google knows it, where its own pages and
 * its API are served from, and the key attempts are found by.
 *
 * @param googleClientId The OAuth client id Google registered the application under.
 * @param googleClientSecret The client's secret, sent to Google when a code is traded.
 * @param appOrigin Where the pages are served, such as `https://app.tallyvane.com`: the browser lands
 * there after Google. No trailing slash.
 * @param apiOrigin Where the API is served, such as `https://api.tallyvane.com`. Google sends the
 * browser back to `<apiOrigin>/api/v1/google-return`, which must be registered with it.
 * @param adminOrigin Where the administrators' site is served, such as `https://admin.tallyvane.com`: its
 * own host, behind Cloudflare Access (ADR-032). Google sends an administrator back to
 * `<adminOrigin>/api/v1/google-return`, which must be registered with it as well (ADR-097). No trailing slash.
 * @param tokenPepper The key under which a browser's secret becomes the digest the database keeps.
 * @param pepperVersion Which pepper this is, kept beside each digest so a rotation can tell them apart.
 * @param totpKeyset The Tink keyset, as JSON, that seals the seeds of TOTP enrolments in the database
 * (ADR-093). Losing it loses every seed: the people who had one turn TOTP on again.
 */
public class SignInConfiguration(
    public val googleClientId: String,
    public val googleClientSecret: Secret,
    public val appOrigin: String,
    public val apiOrigin: String,
    public val adminOrigin: String,
    public val tokenPepper: Secret,
    public val pepperVersion: Int,
    public val totpKeyset: Secret,
) {
    /**
     * The address registered with Google, to which it sends the browser back from the console.
     */
    public fun appRedirectUri(): String = "$apiOrigin/api/v1/google-return"

    /**
     * The address registered with Google, to which it sends the browser back from the administrators' site.
     */
    public fun adminRedirectUri(): String = "$adminOrigin/api/v1/google-return"

    /**
     * What an authenticator app says a TOTP code is for: the product's name.
     */
    public val totpIssuer: String = "Tallyvane"

    override fun toString(): String =
        "SignInConfiguration(appOrigin=$appOrigin, apiOrigin=$apiOrigin, adminOrigin=$adminOrigin)"
}
