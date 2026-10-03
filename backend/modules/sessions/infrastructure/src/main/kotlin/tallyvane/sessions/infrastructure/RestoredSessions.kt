package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.ResultRow
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.SessionId

/**
 * A [Session] from the rows that describe it, one per factor that proved it.
 */
internal class RestoredSessions(private val devices: StoredDevices, private val factors: StoredFactors) {
    fun from(rows: List<ResultRow>): Session {
        val head = rows.first()
        return Session.restore { record ->
            record.session(
                SessionId(head[SessionsTable.id]),
                head[SessionsTable.accountId],
                devices.clientFrom(head[SessionsTable.clientType]),
                head[SessionsTable.authenticatedAt],
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
