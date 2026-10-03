package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * The key to put in an authenticator app, told once: as text to type and as the `otpauth://` address a QR
 * code carries.
 */
@Serializable
internal class TotpStarted(val key: String, val uri: String)
