package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import tallyvane.identity.application.port.EmailMfaEnrollmentStore
import tallyvane.identity.domain.user.UserId

internal class EmailMfaEnrollmentStoreOverExposed : EmailMfaEnrollmentStore {
    override suspend fun enroll(userId: UserId) {
        EmailMfaEnrollmentsTable.insertIgnore { it[EmailMfaEnrollmentsTable.userId] = userId.value }
    }

    override suspend fun unenroll(userId: UserId) {
        EmailMfaEnrollmentsTable.deleteWhere { EmailMfaEnrollmentsTable.userId eq userId.value }
    }

    override suspend fun isEnrolled(userId: UserId): Boolean =
        EmailMfaEnrollmentsTable.selectAll().where { EmailMfaEnrollmentsTable.userId eq userId.value }.limit(1).singleOrNull() != null
}
