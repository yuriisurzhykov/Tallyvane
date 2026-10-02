package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * What the welcome form is prefilled with: the name and address Google gave.
 */
@Serializable
internal class Welcomed(val name: String, val email: String)
