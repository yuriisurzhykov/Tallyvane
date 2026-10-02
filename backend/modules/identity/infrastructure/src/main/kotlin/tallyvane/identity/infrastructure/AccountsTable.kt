package tallyvane.identity.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `identity.accounts`: one row per person.
 */
internal object AccountsTable : Table("identity.accounts") {
    val id = uuid("id")
    val displayName = text("display_name")
    val email = text("email")
    val registeredAt = timestamp("registered_at")
    val consentedAt = timestamp("consented_at")

    override val primaryKey = PrimaryKey(id)
}
