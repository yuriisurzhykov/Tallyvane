package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import tallyvane.identity.application.port.TotpEnrollmentStore
import tallyvane.identity.domain.secondfactor.EncryptedSecret
import tallyvane.identity.domain.secondfactor.totp.TotpEnrollment
import tallyvane.identity.domain.user.UserId

/**
 * [TotpEnrollmentStore] over [TotpEnrollmentsTable], for a real Postgres. Opens no transaction of
 * its own — see that port's own KDoc for why.
 *
 * [save] is an `upsert` rather than an `insert`, matching the port's own contract: a second call
 * for the same [UserId] — [tallyvane.identity.application.secondfactor.ConfirmSecondFactorEnrollmentUseCase]
 * confirming what [tallyvane.identity.application.secondfactor.EnrollSecondFactorUseCase] started
 * — rewrites the one row instead of colliding on the primary key.
 */
internal class TotpEnrollmentStoreOverExposed(private val realm: IdentityRealm = IdentityRealm.USER) :
    TotpEnrollmentStore {
    private val instant = InstantColumn()
    private val table: TotpEnrollmentRowsTable = when (realm) {
        IdentityRealm.USER -> TotpEnrollmentsTable
        IdentityRealm.ADMIN -> AdminTotpEnrollmentsTable
    }

    override suspend fun save(enrollment: TotpEnrollment) {
        table.upsert {
            it[table.userId] = enrollment.userId.value
            it[table.encryptedSecret] = enrollment.secret.value
            it[table.active] = enrollment.active
            it[table.createdAt] = instant.toColumn(enrollment.createdAt)
        }
    }

    override suspend fun find(userId: UserId): TotpEnrollment? = table
        .selectAll()
        .where { table.userId eq userId.value }
        .singleOrNull()
        ?.let { row ->
            TotpEnrollment(
                userId = UserId(row[table.userId]),
                secret = EncryptedSecret(row[table.encryptedSecret]),
                active = row[table.active],
                createdAt = instant.toDomain(row[table.createdAt]),
            )
        }

    override suspend fun delete(userId: UserId) {
        table.deleteWhere { table.userId eq userId.value }
    }
}
