package tallyvane.identity.application.googlecredential

import tallyvane.identity.domain.session.DeviceLabel

public data class SignInWithGoogleCredentialRequest(public val idToken: String, public val device: DeviceLabel)
