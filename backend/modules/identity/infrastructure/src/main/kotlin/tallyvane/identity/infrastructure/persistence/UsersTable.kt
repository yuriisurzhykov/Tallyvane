package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Mirrors `identity.users` from `V20260902000000__identity_schema.sql` column for column — see
 * that migration for why `email` carries `platform.case_insensitive` at the database level; this
 * declaration cannot express a collation, so the guarantee lives in the migration, not here.
 */
internal object UsersTable : Table("identity.users") {
    val id = uuid("id")
    val email = text("email")
    val displayName = text("display_name").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val disabledAt = timestampWithTimeZone("disabled_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
