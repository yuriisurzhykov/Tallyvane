package tallyvane.identity.web.oauth

internal data class GoogleOAuthConfiguration(
    val clientId: String,
    val redirectUri: String,
    val secure: Boolean,
    val cookiePath: String,
)
