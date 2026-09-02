package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Mirrors `identity.google_credentials` from `V20260902000000__identity_schema.sql`.
 */
internal object GoogleCredentialsTable : Table("identity.google_credentials") {
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val googleSubject = text("google_subject").uniqueIndex()

    override val primaryKey = PrimaryKey(userId)
}
