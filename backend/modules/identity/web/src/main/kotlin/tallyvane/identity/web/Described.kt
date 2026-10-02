package tallyvane.identity.web

import kotlinx.serialization.Serializable

/**
 * What `GET /me` answers: which account, and what it is called.
 */
@Serializable
internal class Described(val id: String, val name: String)
