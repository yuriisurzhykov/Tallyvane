package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * The answer to starting a sign-in: where to send the browser.
 */
@Serializable
internal class SignInStarted(val authorizationUrl: String)
