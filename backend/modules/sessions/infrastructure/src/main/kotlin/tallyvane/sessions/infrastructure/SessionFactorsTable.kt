package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.Table

/**
 * `sessions.session_factors`: the ways a session's person proved who they are.
 */
internal object SessionFactorsTable : Table("sessions.session_factors") {
    val sessionId = uuid("session_id")
    val factor = text("factor")

    override val primaryKey = PrimaryKey(sessionId, factor)
}
