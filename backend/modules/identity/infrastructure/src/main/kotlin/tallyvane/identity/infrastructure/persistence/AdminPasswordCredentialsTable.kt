package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

internal object AdminPasswordCredentialsTable : Table("identity.admin_password_credentials") {
    val adminId = uuid("admin_id").references(AdminsTable.id, onDelete = ReferenceOption.CASCADE)
    val passwordHash = text("password_hash")

    override val primaryKey = PrimaryKey(adminId)
}
