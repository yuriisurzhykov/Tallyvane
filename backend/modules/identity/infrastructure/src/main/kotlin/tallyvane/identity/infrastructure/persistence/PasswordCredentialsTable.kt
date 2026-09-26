package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Mirrors `identity.password_credentials` from `V20260902000000__identity_schema.sql`.
 */
internal object PasswordCredentialsTable : Table("identity.password_credentials") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val passwordHash = text("password_hash")

    override val primaryKey = PrimaryKey(userId)
}
