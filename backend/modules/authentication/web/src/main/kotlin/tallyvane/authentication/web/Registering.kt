package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * What the welcome form sends: the name the person chose and whether they agreed to the privacy policy.
 * An absent `agreed` is a no.
 */
@Serializable
internal class Registering(val name: String, val agreed: Boolean = false)
