package tallyvane.authentication.infrastructure

/**
 * Where Google's three addresses are: the page the person signs in on, the endpoint a code is traded
 * at, and the published keys its ID tokens are signed with. Held together so a test can stand a
 * provider of its own in Google's place and the adapter is exercised for real.
 *
 * @param issuer The `iss` a genuine token carries.
 */
internal class GoogleEndpoints(val authorization: String, val token: String, val keys: String, val issuer: String) {
    override fun toString(): String = "GoogleEndpoints($authorization)"

    companion object {
        /**
         * Google's own.
         */
        fun google(): GoogleEndpoints = GoogleEndpoints(
            authorization = "https://accounts.google.com/o/oauth2/v2/auth",
            token = "https://oauth2.googleapis.com/token",
            keys = "https://www.googleapis.com/oauth2/v3/certs",
            issuer = "https://accounts.google.com",
        )
    }
}
