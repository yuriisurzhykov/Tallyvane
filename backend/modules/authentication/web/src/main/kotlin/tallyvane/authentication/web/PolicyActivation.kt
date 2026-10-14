package tallyvane.authentication.web

import kotlinx.serialization.Serializable

/**
 * Which version to put in force, and why: the version [number] of [purpose], for the reason [comment].
 */
@Serializable
internal class PolicyActivation(val purpose: String, val number: Int, val comment: String)
