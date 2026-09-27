package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.domain.user.UserId

internal class EmailMfaEnrollmentStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) :
    EmailMfaEnrollmentStore {
    private val table: EmailMfaEnrollmentRowsTable = when (realm) {
        IdentityRealm.USER -> EmailMfaEnrollmentsTable
        IdentityRealm.ADMIN -> AdminEmailMfaEnrollmentsTable
    }

    override suspend fun enroll(userId: UserId) {
        table.insertIgnore { it[table.userId] = userId.value }
    }

    override suspend fun unenroll(userId: UserId) {
        table.deleteWhere { table.userId eq userId.value }
    }

    override suspend fun isEnrolled(userId: UserId): Boolean = table.selectAll().where {
        table.userId eq userId.value
    }.limit(1).singleOrNull() !=
        null
}
