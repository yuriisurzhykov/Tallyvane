package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.SessionId

/**
 * A [Session] from the rows that describe it, one per factor that proved it.
 */
internal class RestoredSessions(private val devices: StoredDevices, private val factors: StoredFactors) {
    /**
     * What storage holds to restore sessions from: a row for each factor that proved each of them.
     */
    fun rows() = SessionsTable
        .join(SessionFactorsTable, JoinType.INNER) { SessionsTable.id eq SessionFactorsTable.sessionId }
        .selectAll()

    fun from(rows: List<ResultRow>): Session {
        val head = rows.first()
        return Session.restore { record ->
            record.session(
                SessionId(head[SessionsTable.id]),
                head[SessionsTable.accountId],
                devices.clientFrom(head[SessionsTable.clientType]),
                head[SessionsTable.authenticatedAt],
                head[SessionsTable.confirmedAt],
                head[SessionsTable.lastActiveAt],
            )
            record.device(
                devices.browserFrom(head[SessionsTable.deviceBrowser]),
                devices.platformFrom(head[SessionsTable.devicePlatform]),
                head[SessionsTable.deviceMobile],
                head[SessionsTable.deviceName],
            )
            rows.forEach { record.proved(factors.from(it[SessionFactorsTable.factor])) }
        }
    }

    override fun toString(): String = "RestoredSessions"
}
