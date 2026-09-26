package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.Table

internal object EmailMfaEnrollmentsTable : Table("identity.email_mfa_enrollments") {
    val userId = uuid("user_id")
    override val primaryKey = PrimaryKey(userId)
}
