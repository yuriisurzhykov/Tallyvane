package tallyvane.sessions.web

import kotlinx.serialization.Serializable

/**
 * That a version was kept, and the number it was given among the versions of its [client]. It is not in force.
 */
@Serializable
internal class LifetimeVersionMade(val client: String, val number: Int)
