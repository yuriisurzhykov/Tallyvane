package tallyvane.sessions.web

import kotlinx.serialization.Serializable

/**
 * Which version to put in force, and why: the version [number] of [client], for the reason [comment].
 */
@Serializable
internal class LifetimeActivation(val client: String, val number: Int, val comment: String)
