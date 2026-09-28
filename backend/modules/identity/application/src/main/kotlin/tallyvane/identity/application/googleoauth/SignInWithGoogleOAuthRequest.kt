package tallyvane.identity.application.googleoauth

import tallyvane.identity.domain.session.DeviceLabel

public data class SignInWithGoogleOAuthRequest(
    public val code: String,
    public val codeVerifier: String,
    public val redirectUri: String,
    public val device: DeviceLabel,
)
