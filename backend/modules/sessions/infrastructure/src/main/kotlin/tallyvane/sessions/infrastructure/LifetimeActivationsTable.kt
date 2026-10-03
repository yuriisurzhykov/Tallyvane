package tallyvane.sessions.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `sessions.lifetime_activations`: which version was put in force, and when. The last row of a kind of
 * client is the one in force.
 */
internal object LifetimeActivationsTable : Table("sessions.lifetime_activations") {
    val id = long("id").autoIncrement()
    val clientType = text("client_type")
    val number = integer("number")
    val activatedAt = timestamp("activated_at")

    override val primaryKey = PrimaryKey(id)
}
