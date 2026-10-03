package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `sessions.lifetime_versions`: one row per version of the lifetimes of a kind of client.
 */
internal object LifetimeVersionsTable : Table("sessions.lifetime_versions") {
    val clientType = text("client_type")
    val number = integer("number")
    val idleMillis = long("idle_millis")
    val absoluteMillis = long("absolute_millis")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(clientType, number)
}
