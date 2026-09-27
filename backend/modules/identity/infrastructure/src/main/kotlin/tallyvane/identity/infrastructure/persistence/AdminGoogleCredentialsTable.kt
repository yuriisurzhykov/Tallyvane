package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

internal object AdminGoogleCredentialsTable : Table("identity.admin_google_credentials") {
    val adminId = uuid("admin_id").references(AdminsTable.id, onDelete = ReferenceOption.CASCADE)
    val googleSubject = text("google_subject")

    override val primaryKey = PrimaryKey(adminId)
}
