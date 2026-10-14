package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * That a version was kept, and the number it was given among the versions of its [purpose]. It is not in force.
 */
@Serializable
internal class PolicyVersionMade(val purpose: String, val number: Int)
