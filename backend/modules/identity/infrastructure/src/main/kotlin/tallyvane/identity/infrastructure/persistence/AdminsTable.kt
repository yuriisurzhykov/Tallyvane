package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

internal object AdminsTable : Table("identity.admins") {
    val id = uuid("id")
    val email = text("email")
    val displayName = text("display_name").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val disabledAt = timestampWithTimeZone("disabled_at").nullable()
    val emailVerified = bool("email_verified")

    override val primaryKey = PrimaryKey(id)
}
