package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * The first code the authenticator shows, which turns TOTP on.
 */
@Serializable
internal class FirstCode(val code: String)
