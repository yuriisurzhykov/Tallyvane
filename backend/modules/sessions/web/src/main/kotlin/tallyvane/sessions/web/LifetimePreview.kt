package tallyvane.sessions.web

import kotlinx.serialization.Serializable

/**
 * What putting a version in force would do: [sessions] is how many sessions of its kind of client are live now, and
 * [wouldEnd] how many of them it would end at their next request.
 */
@Serializable
internal class LifetimePreview(val sessions: Int, val wouldEnd: Int)
