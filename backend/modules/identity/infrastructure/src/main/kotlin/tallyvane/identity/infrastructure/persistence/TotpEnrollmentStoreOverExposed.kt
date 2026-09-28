package tallyvane.identity.infrastructure.persistence

import org.jetbrains.exposed.v1.core.eq
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
internal class TotpEnrollmentStoreOverExposed : TotpEnrollmentStore {
    private val instant = InstantColumn()

    override suspend fun save(enrollment: TotpEnrollment) {
        TotpEnrollmentsTable.upsert {
            it[userId] = enrollment.userId.value
            it[encryptedSecret] = enrollment.secret.value
            it[active] = enrollment.active
            it[createdAt] = instant.toColumn(enrollment.createdAt)
        }
    }

    override suspend fun find(userId: UserId): TotpEnrollment? = TotpEnrollmentsTable
        .selectAll()
        .where { TotpEnrollmentsTable.userId eq userId.value }
        .singleOrNull()
        ?.let { row ->
            TotpEnrollment(
                userId = UserId(row[TotpEnrollmentsTable.userId]),
                secret = EncryptedSecret(row[TotpEnrollmentsTable.encryptedSecret]),
                active = row[TotpEnrollmentsTable.active],
                createdAt = instant.toDomain(row[TotpEnrollmentsTable.createdAt]),
            )
        }
}
